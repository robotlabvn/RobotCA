package com.robotca.ControlApp;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.DisplayMetrics;
import android.util.Log;

import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;


import com.robotca.ControlApp.Core.ControlMode;
import com.robotca.ControlApp.Core.DrawerItem;
import com.robotca.ControlApp.Core.IWaypointProvider;
import com.robotca.ControlApp.Core.NavDrawerAdapter;
import com.robotca.ControlApp.Core.Plans.RandomWalkPlan;
import com.robotca.ControlApp.Core.Plans.WaypointPlan;
import com.robotca.ControlApp.Core.RobotController;
import com.robotca.ControlApp.Core.RobotInfo;
import com.robotca.ControlApp.Core.RobotStorage;
import com.robotca.ControlApp.Fragments.CameraViewFragment;
import com.robotca.ControlApp.Fragments.HUDFragment;
import com.robotca.ControlApp.Fragments.JoystickFragment;
import com.robotca.ControlApp.Fragments.RosFragment;
import com.robotca.ControlApp.Fragments.LaserScanFragment;
import com.robotca.ControlApp.Fragments.MapFragment;
import com.robotca.ControlApp.Fragments.OverviewFragment;
import com.robotca.ControlApp.Fragments.PreferencesFragment;

import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.rosjava_geometry.Vector3;

import java.util.ArrayList;
import java.util.List;

public class ControlApp extends RosActivity implements ListView.OnItemClickListener, IWaypointProvider {
    public static String NOTIFICATION_TICKER = "ROS Control";
    public static String NOTIFICATION_TITLE = "ROS Control";
    public static RobotInfo ROBOT_INFO;
    //public static URI DEFAULT_URI = URI.create("localhost");

    private String[] mFeatureTitles;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    private NodeMainExecutor nodeMainExecutor;
    private NodeConfiguration nodeConfiguration;
    private ActionBarDrawerToggle mDrawerToggle;
    //creating emergency stop button
    private Button emergencyStop;
    private JoystickFragment joystickFragment;
    private HUDFragment hudFragment;
    private RobotController controller;

    private int drawerIndex = 1;
    private String mTitle;
    private String mDrawerTitle;

    private static final String TAG = "ControlApp";
    private Vector3 waypoint;

    public ControlApp() {
        super(NOTIFICATION_TICKER, NOTIFICATION_TITLE, ROBOT_INFO.getUri());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(ROBOT_INFO != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("edittext_joystick_topic", ROBOT_INFO.getJoystickTopic());
            editor.putString("edittext_laser_scan_topic", ROBOT_INFO.getLaserTopic());
            editor.putString("edittext_camera_topic", ROBOT_INFO.getCameraTopic());

            editor.apply();
        }

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;

        if (dpWidth >= 550) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setContentView(R.layout.main);

        mFeatureTitles = getResources().getStringArray(R.array.feature_titles);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        mTitle = mDrawerTitle = ROBOT_INFO.getName(); //getTitle().toString();

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
               /* R.drawable.ic_drawer,*/ R.string.drawer_open,
                R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                //getActionBar().setTitle(mTitle);
                invalidateOptionsMenu(); // creates call to
                // onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                //getActionBar().setTitle(mDrawerTitle);
                invalidateOptionsMenu(); // creates call to
                // onPrepareOptionsMenu()
            }
        };

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

//        if (savedInstanceState == null) {
//            drawerIndex = 1;
//        }

        //int[] featureIconRes = getResources().getIntArray(R.array.feature_icons);
        
        int[] imgRes = new int[]{
                R.drawable.ic_android_black_24dp,
                R.drawable.ic_view_quilt_black_24dp,
                R.drawable.ic_linked_camera_black_24dp,
                R.drawable.ic_navigation_black_24dp,
                R.drawable.ic_terrain_black_24dp,
                R.drawable.ic_settings_black_24dp
        };

        List<DrawerItem> drawerItems = new ArrayList<>();

        for (int i = 0; i < mFeatureTitles.length; i++) {
            drawerItems.add(new DrawerItem(mFeatureTitles[i], imgRes[i]));
        }

        NavDrawerAdapter drawerAdapter = new NavDrawerAdapter(this,
                R.layout.nav_drawer_menu_item,
                drawerItems);

        mDrawerList.setAdapter(drawerAdapter);
        mDrawerList.setOnItemClickListener(this);

        // Joystick fragment
        joystickFragment = (JoystickFragment) getFragmentManager().findFragmentById(R.id.joystick_fragment);
        controller = new RobotController(this);

        // Hud fragment
        hudFragment = (HUDFragment) getFragmentManager().findFragmentById(R.id.hud_fragment);

        // Emergency stop button
        if (emergencyStop == null) {
            try {
                emergencyStop = (Button) hudFragment.getView().findViewById(R.id.emergencyStop);
                emergencyStop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        controller.stop();
                        joystickFragment.stop();
                    }
                });
            }
            catch(Exception e){}
        }

        setDestination(new Vector3(6, 0, 0));
    }

    @Override
    protected void onStop() {
        RobotStorage.update(this, ROBOT_INFO);

        Log.d(TAG, "onStop()");

        if(controller != null)
            controller.stop();

        if(joystickFragment != null)
            joystickFragment.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");

        if(controller != null)
            controller.stop();

        if(joystickFragment != null)
            joystickFragment.stop();

        //this.nodeMainExecutorService.forceShutdown();
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {

        try {
            java.net.Socket socket = new java.net.Socket(getMasterUri().getHost(), getMasterUri().getPort());
            java.net.InetAddress local_network_address = socket.getLocalAddress();
            socket.close();

            this.nodeMainExecutor = nodeMainExecutor;
            this.nodeConfiguration =
                    NodeConfiguration.newPublic(local_network_address.getHostAddress(), getMasterUri());

            joystickFragment.initialize(this.nodeMainExecutor, this.nodeConfiguration);
            joystickFragment.invalidate();

            hudFragment.initialize(this.nodeMainExecutor, this.nodeConfiguration);

            //controller.setTopicName(PreferenceManager.getDefaultSharedPreferences(this).getString("edittext_joystick_topic", getString(R.string.joy_topic)));
            controller.initialize(nodeMainExecutor, nodeConfiguration);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View temp = mDrawerList.getChildAt(drawerIndex);
                    mDrawerList.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, temp.getX(), temp.getY(), 0));

                    selectItem(drawerIndex);

                    // Add HUD to joystick odometry subscriber
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            while (!joystickFragment.getJoystickView().addOdometryListener(hudFragment)) {
                                Log.w(TAG, "Unable to add HUD to joystick odometry listener");
                                try
                                {
                                    Thread.sleep(100L);
                                }
                                catch (InterruptedException e)
                                {
                                    Log.e(TAG, "", e);
                                    return;
                                }
                            }
                            Log.d(TAG, "Added HUD to joystick odometry listener!");
                        }
                    }, 100L);

                }
            });
        } catch (Exception e) {
            // Socket problem
            Log.e(TAG, "socket error trying to get networking information from the master uri", e);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selectItem(position);
    }

    /**
     * Locks/unlocks the screen orientation.
     * Adapted from an answer on StackOverflow by jp36
     * @param lock Whether to lock the orientation
     */
    public void lockOrientation(boolean lock) {

        if (lock) {
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

            int rotation = display.getRotation();
            int tempOrientation = getResources().getConfiguration().orientation;
            int orientation = 0;

            switch (tempOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Configuration.ORIENTATION_PORTRAIT:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
            }

            //noinspection ResourceType
            setRequestedOrientation(orientation);
        }
        else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    /**
     * Swaps fragments in the main content view
     */
    private void selectItem(int position) {

        Fragment fragment = null;
        Bundle args = new Bundle();

        if(joystickFragment != null && getControlMode().ordinal() < ControlMode.Waypoint.ordinal()){
            joystickFragment.show();
        }

        if (hudFragment != null) {
            hudFragment.show();
        }

        if(controller != null) {
            controller.update();
        }

        switch (position) {
            case 0:
                Log.d(TAG, "Drawer item 0 selected, finishing");
                finish();
                return;

            case 1:
                fragment = new OverviewFragment();
                break;

            case 2:
                fragment = new CameraViewFragment();
                break;

            case 3:
                fragment = new LaserScanFragment();
                break;

            case 4:
                fragment = new MapFragment();
                break;

            case 5:
                if (hudFragment != null)
                    hudFragment.hide();
                if (joystickFragment != null)
                    joystickFragment.hide();
                fragment = new PreferencesFragment();
                break;

            default:
                break;
        }

        try {
            //noinspection ConstantConditions
            ((RosFragment) fragment).initialize(nodeMainExecutor, nodeConfiguration);
        } catch (Exception e) {
            // Ignore
        }

        if (fragment != null) {
            fragment.setArguments(args);

            // Insert the fragment by replacing any existing fragment
            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }

        // Highlight the selected item, update the title, and close the drawer
        mDrawerList.setItemChecked(position, true);
        mDrawerLayout.closeDrawer(mDrawerList);
        setTitle(mFeatureTitles[position]);
    }

    @Override
    public void setTitle(CharSequence title) {
        try {
            //noinspection ConstantConditions
            getActionBar().setTitle(title);
        } catch (NullPointerException e) {
            // Ignore
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        switch(item.getItemId()){
            case R.id.action_joystick_control:
                setControlMode(ControlMode.Joystick);
                return true;

            case R.id.action_motion_control:
                setControlMode(ControlMode.Motion);
                return true;

            case R.id.action_waypoint_control:
                setControlMode(ControlMode.Waypoint);
                return true;

            case R.id.action_random_walk_control:
                setControlMode(ControlMode.RandomWalk);
                return true;

            default:
                return mDrawerToggle.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        for(int i = 0; i < menu.size(); i++){
            menu.getItem(i).setChecked(false);
        }

        menu.getItem(1).setEnabled(joystickFragment.hasAccelerometer());
        menu.getItem(getControlMode().ordinal()).setChecked(true);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_control_app, menu);
        menu.getItem(0).setChecked(true);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    public ControlMode getControlMode() {
        return joystickFragment.getControlMode();
    }

    public void setControlMode(ControlMode controlMode) {
        lockOrientation(controlMode == ControlMode.Motion);
        joystickFragment.setControlMode(controlMode);

        if(getControlMode() == ControlMode.Waypoint){
            controller.runPlan(new WaypointPlan(this));
        }
        else if(controlMode == ControlMode.RandomWalk) {
            controller.runPlan(new RandomWalkPlan(
                    Float.parseFloat(PreferenceManager
                            .getDefaultSharedPreferences(this)
                    .getString("edittext_random_walk_range_proximity", "1"))
            ));
        }
        else{
            controller.stop();
        }

        invalidateOptionsMenu();
    }

    public void setDestination(Vector3 location){
        waypoint = location;
    }

    @Override
    public Vector3 getDestination() {
        return waypoint;
    }

    public RobotController getController(){return controller;}
}
