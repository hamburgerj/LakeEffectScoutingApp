package ca.lakeeffect.scoutingapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.percent.PercentRelativeLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //TODO: Redo text sizes

    List<Counter> counters = new ArrayList<>();
    List<CheckBox> checkboxes = new ArrayList<>();
    List<RadioGroup> radiogroups = new ArrayList<>();
    List<Button> buttons = new ArrayList<>();
    List<SeekBar> seekbars = new ArrayList<>();

    TextView timer;
    TextView robotNumText; //robotnum and round

    int robotNum = 2708;
    int round = -1;

    //Robot schedule for each user (by user ID)
    //the username selection screen will show a spinner with all the names in this list
    //FUTURE: Maybe pull thses names from the server? Grab them from a text file?
    //Add one to the index as there is one placeholder default value
    ArrayList<UserData> schedules = new ArrayList<>();

    //the id of the user currently scouting. This decides when they must switch on and off from scouting
    int userID = 0;

    //the field data
    public static boolean alliance; //red is false, true is blue
    public static boolean side; //red on left is false, blue on left is true

    InputPagerAdapter pagerAdapter;
    ViewPager viewPager;

    ArrayList<String> pendingmessages = new ArrayList<>();
    boolean connected;

    ListenerThread listenerThread;
    Thread listenerThreadThreadClass;

    String savedLabels = null; //generated at the beginning

    int versionCode;

    //used to make sure the robot selected is actually at the competition
    String[] availableRobots;

    //the last time submit has been pressed
    //used to see if "are you still here" messages should be placed
    public static long lastSubmit = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //set version code
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

//        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);
////        ActionBar actionBar = getActionBar();
//        actionBar.hide();


        //check what theme is selected and set it as the theme
        SharedPreferences prefs1 = getSharedPreferences("theme", MODE_PRIVATE);
        switch (prefs1.getInt("theme", 0)) {
            case 0:
                setTheme(R.style.AppTheme);
                break;
            case 1:
                setTheme(R.style.AppThemeLight);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //call alert (asking scout name and robot number)
        alert();

        //add all buttons and counters etc.

        //go through all saved pending messages and add them to the variable
        SharedPreferences prefs = getSharedPreferences("pendingmessages", MODE_PRIVATE);
        int messageAmount = prefs.getInt("messageAmount", 0);
        for (int i = 0; i < messageAmount; i++) {
            if (prefs.getString("message" + i, null) == null) {
                messageAmount ++;
                i++;
                if(i > 150){
                    break;
                }
            } else {
                pendingmessages.add(prefs.getString("message" + i, ""));
            }
        }

        //reset the amount of pending messages
        SharedPreferences prefs2 = getSharedPreferences("pendingmessages", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor2 = prefs2.edit();
        editor2.putInt("messageAmount", pendingmessages.size());
        editor2.apply();

        //set device name
        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        ((TextView) ((RelativeLayout) findViewById(R.id.deviceNameLayout)).findViewById(R.id.deviceName)).setText(ba.getName()); //if this method ends up not working refer to https://stackoverflow.com/a/6662271/1985387

        //set pending messages number on ui
        ((TextView) ((RelativeLayout) findViewById(R.id.numberOfPendingMessagesLayout)).findViewById(R.id.numberOfPendingMessages)).setText(pendingmessages.size() + "");


//        counters.add((Counter) findViewById(R.id.goalsCounter));

        //setup scrolling viewpager
        viewPager = (ViewPager) findViewById(R.id.scrollingview);
        pagerAdapter = new InputPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(3);

//        NumberPicker np = (NumberPicker) findViewm counters
//        np.setWrapSelectorWheel(false);ById(R.id.numberPicker);
//
//        np.setMinValue(0);
//        np.setMaxValue(20);    //maybe switch fro
//        np.setValue(0);

        //add onClickListeners

//        checkboxes.add((CheckBox) findViewById(R.id.scaleCheckBox));

//        submit = (Button) findViewById(R.id.submitButton);

//        timer = (TextView) findViewById(R.id.timer);
        robotNumText = (TextView) findViewById(R.id.robotNum);

        robotNumText.setText("Round: " + round + "  Robot: " + robotNum);

//        submit.setOnClickListener(this);

        //load available robots for this competition
        InputStream is = getResources().openRawResource(R.raw.robotnumbers);
        try {
            String s = IOUtils.toString(is);

            availableRobots = s.split("\n");

            //For some reason IOUtils spits out text with an extra character, this code fixes that
            for(int i = 0; i < availableRobots.length; i++) {
                availableRobots[i] = availableRobots[i].substring(0, availableRobots[i].length() - 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        IOUtils.closeQuietly(is);

        //Temperarily set a predifined schedule to use for the robot numbers displayed per round.
        //This will be replaced with data sent directly from the server
        schedules = new ArrayList<>();
        ArrayList<Integer> firstSchedule = new ArrayList<>();
        ArrayList<Boolean> firstAlliances = new ArrayList<>();
        firstSchedule.add(2708);
        firstAlliances.add(true);
        firstSchedule.add(2809);
        firstAlliances.add(false);
        firstSchedule.add(254);
        firstAlliances.add(false);
        firstSchedule.add(2056);
        firstAlliances.add(true);
        firstSchedule.add(1114);
        firstAlliances.add(false);
        firstSchedule.add(1511);
        firstAlliances.add(true);
        schedules.add(new UserData(0, "Ajay", firstAlliances, firstSchedule));

    }

    public void restartListenerThread(){
        stopListenerThread();

        startListenerThread();
    }

    public void stopListenerThread() {
        if (listenerThread != null) {
            if(listenerThread.connectionThreadThreadClass != null){
                try {
                    listenerThread.connectionThreadThreadClass.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else{
                if(listenerThreadThreadClass != null) {
                    try {
                        listenerThreadThreadClass.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void startListenerThread() {
        if (savedLabels == null) savedLabels = getData(true)[1];

        //start listening
        if (listenerThread == null) {
            listenerThread = new ListenerThread(this);
            listenerThreadThreadClass = new Thread(listenerThread);
            listenerThreadThreadClass.start();
        }
    }

    StringBuilder data;
    StringBuilder labels;

    public String getEventData(){
        StringBuilder events = new StringBuilder();

        for(Event event : pagerAdapter.teleopPage.events){

            int location = event.location;

            //if reds on the left, and the robot is on blue alliance, or blue is on the left, and the robot is on the blue alliance
            if((!side && alliance) || (side && !alliance)){
                location = flipLocation(location);
            }

            events.append(round + "," + event.eventType + "," + location + "," + event.timestamp + "," + event.metadata + "\n");
        }

        return events.toString();
    }

    //will return the same location but on the other side of the field
    public static int flipLocation(int location){
        switch (location){
            case 0:
                return 11;
            case 1:
                return 12;
            case 2:
                return 13;
            case 3:
                return 10;
            case 4:
                return 8;
            case 5:
                return 9;
            case 6:
                return 6;
            case 7:
                return 7;
            case 8:
                return 4;
            case 9:
                return 5;
            case 10:
                return 3;
            case 11:
                return 0;
            case 12:
                return 1;
            case 13:
                return 2;
        }

        return 0;
    }

    public String[] getData(boolean bypassChecks) {
        if (!bypassChecks) {
            if (((RatingBar) pagerAdapter.endgamePage.getView().findViewById(R.id.driveRating)).getRating() <= 0) {
                runOnUiThread(new Thread() {
                    public void run() {
                        new Toast(MainActivity.this).makeText(MainActivity.this, "You didn't rate the drive ability!", Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }

            if (((RatingBar) pagerAdapter.endgamePage.getView().findViewById(R.id.intakeRating)).getRating() <= 0) {
                runOnUiThread(new Thread() {
                    public void run() {
                        new Toast(MainActivity.this).makeText(MainActivity.this, "You didn't rate the intake ability!", Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }

            if (((RadioGroup) pagerAdapter.autoPage.getView().findViewById(R.id.autoBaselineGroup)).getCheckedRadioButtonId() == -1) {
                runOnUiThread(new Thread() {
                    public void run() {
                        new Toast(MainActivity.this).makeText(MainActivity.this, "You forgot to specify if it crossed the baseline! Go back to the auto page!", Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }

            if (((Spinner) pagerAdapter.endgamePage.getView().findViewById(R.id.endgameClimb)).getSelectedItem().toString().equals("Choose One")) {
                runOnUiThread(new Thread() {
                    public void run() {
                        new Toast(MainActivity.this).makeText(MainActivity.this, "You forgot to specify if it climbed!", Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
        }

        data = new StringBuilder();
        labels = new StringBuilder();

        //General Info
        data.append(round + ",");
        labels.append("Match,");

        labels.append("Date and Time Of Match,");
        DateFormat dateFormat = new SimpleDateFormat("dd HH;mm;ss");
        Date date = new Date();
        data.append(dateFormat.format(date) + ",");


        PercentRelativeLayout layout;

        //Auto page
        layout = (PercentRelativeLayout) pagerAdapter.autoPage.getView().findViewById(R.id.autoPageLayout);
        enterLayout(layout);

//        //Tele page
//        layout = (PercentRelativeLayout) pagerAdapter.teleopPage.getView().findViewById(R.id.telePageLayout);
//        enterLayout(layout);

        String[] tele = pagerAdapter.teleopPage.getData();

        labels.append(tele[0]);
        data.append(tele[1]);

        //Endgame page
        layout = (PercentRelativeLayout) pagerAdapter.endgamePage.getView().findViewById(R.id.endgamePageLayout);
        enterLayout(layout);

        labels.append("Scout,\n");
        data.append(schedules.get(userID).userName + ",\n");

        System.out.println(labels.toString());
        System.out.println(data.toString());
        String[] out = {data.toString(), labels.toString()};
        return out;
    }

    void enterLayout(ViewGroup top) {
        //Iterate over all child layouts
        for (int i = 0; i < top.getChildCount(); i++) {
            View v = top.getChildAt(i);
            if (v instanceof EditText) {
                data.append(((EditText) v).getText().toString().replace("|", "||").replace(",", "|c").replace("\n", "|n").replace("\"", "|q").replace(":", ";") + ",");
                labels.append(getName(v) + ",");
            }
            if (v instanceof CheckBox) {
                data.append(((CheckBox) v).isChecked() + ",");
                labels.append(getName(v) + ",");
            }
            if (v instanceof Counter) {
                data.append(((Counter) v).count + ",");
                labels.append(getName(v) + ",");
            }
            if (v instanceof HigherCounter) {
                data.append(((HigherCounter) v).count + ",");
                labels.append(getName(v) + ",");
            }
            if (v instanceof RatingBar) {
                data.append(((RatingBar) v).getRating() + ",");
//                    System.out.println(getName(v));
                labels.append(getName(v) + ",");
            }
            if (v instanceof Spinner) {
                data.append(((Spinner) v).getSelectedItem().toString() + ",");
                System.out.println(((Spinner) v).getSelectedItem().toString() + ",");
                labels.append(getName(v) + ",");
            }
            if (v instanceof RadioGroup) {
                String selected = getName(v.findViewById(((RadioGroup) v).getCheckedRadioButtonId()));
                //Game-specific cases
                switch(selected){
                    //Baseline radiogroup
                    case "Baseline Success":
                        data.append("True,");
                        break;
                    case "Baseline Failed":
                        data.append("False,");
                        break;
                    //Power cube radiogroup
                    case "First Auto Cube Success":
                        data.append("True,");
                        break;
                    case "First Auto Cube Failed":
                        data.append("False,");
                        break;
                    case "Second Auto Cube Success":
                        data.append("True,");
                        break;
                    case "Second Auto Cube Failed":
                        data.append("False,");
                        break;
                    case "Third Auto Cube Success":
                        data.append("True,");
                        break;
                    case "Third Auto Cube Failed":
                        data.append("False,");
                        break;
                    //Radio button ID will be result output in data
                    default:
                         data.append(selected + ",");
                        break;
                }
//                data.append(((RadioGroup) v).getCheckedRadioButtonId() + ",");
                labels.append(getName(v) + ",");
            }
            //If the child is a layout, enter it
            else if (v instanceof ViewGroup) {
                enterLayout((ViewGroup) v);
            }
        }
    }

    //Caps => spaces then letter
    //First letter capital

    String getName(View v) {
        if (v == null || v.getId()==-1) return "NULL";
        String id = getResources().getResourceEntryName(v.getId());
        String out = id.substring(0, 1).toUpperCase() + id.substring(1);
        for (int i = 1; i < out.length(); i++) {
            if (Character.isUpperCase(out.charAt(i))) {
                System.out.println("TEST");
                out = out.substring(0, i) + " " + out.substring(i);
                i++;
            }
        }
        return out;
    }


    public boolean saveData() {
        File sdCard = Environment.getExternalStorageDirectory();
//        File dir = new File (sdCard.getPath() + "/ScoutingData/");

        File file = new File(sdCard.getPath() + "/#ScoutingData/" + robotNum + ".csv");

        try {

            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            String[] data = getData(false);
            if (data == null) {
                return false;
            }

            String events = getEventData();

            File eventsFile = new File(sdCard.getPath() + "/#ScoutingData/EventData/" + robotNum + ".csv");

            eventsFile.getParentFile().mkdirs();
            if (!eventsFile.exists()) {
                eventsFile.createNewFile();
            }

            FileOutputStream eventsF = new FileOutputStream(eventsFile, true);

            OutputStreamWriter eventsOut = new OutputStreamWriter(eventsF);

            eventsOut.append(events);
            eventsOut.close();
            eventsF.close();


            //save to file
            if (newfile) out.append(data[1]);
            out.append(data[0]);

            String fulldata = "";
            if (events.equals("")) {
                fulldata = robotNum + ":" + data[0];
            } else {
                fulldata = robotNum + ":" + data[0] + ":" + events;
            }

            //add to pending messages
            pendingmessages.add(fulldata);
            //add to sharedprefs
            SharedPreferences prefs = getSharedPreferences("pendingmessages", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("message" + prefs.getInt("messageAmount", 0), fulldata);
            editor.putInt("messageAmount", prefs.getInt("messageAmount", 0) + 1);
            editor.apply();

            //set pending messages number on ui
            ((TextView) ((RelativeLayout) findViewById(R.id.numberOfPendingMessagesLayout)).findViewById(R.id.numberOfPendingMessages)).setText(pendingmessages.size() + "");

            out.close();

            f.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void waitForConfirmation(final StringBuilder labels, final StringBuilder data) {
        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    System.out.println("aaaa");
                    byte[] bytes = new byte[1000];
                    try {
                        if (!connected) {
                            pendingmessages.add(robotNum + ":" + labels.toString() + ":" + data.toString());
                            SharedPreferences prefs = getSharedPreferences("pendingmessages", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("message" + prefs.getInt("messageAmount", 0), robotNum + ":" + labels.toString() + ":" + data.toString());
                            editor.putInt("messageAmount", prefs.getInt("messageAmount", 0) + 1);
                            editor.apply();
                            return;
                        }
                        int amount = listenerThread.in.read(bytes);
                        if (new String(bytes, Charset.forName("UTF-8")).equals("done")) {
                            return;
                        }
                        if (!connected) {
                            pendingmessages.add(robotNum + ":" + labels.toString() + ":" + data.toString());
                            SharedPreferences prefs = getSharedPreferences("pendingmessages", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("message" + prefs.getInt("messageAmount", 0), robotNum + ":" + labels.toString() + ":" + data.toString());
                            editor.putInt("messageAmount", prefs.getInt("messageAmount", 0) + 1);
                            editor.apply();
                            return;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    public int getLocationInSharedMessages(String message) {
        SharedPreferences prefs = getSharedPreferences("pendingmessages", MODE_PRIVATE);
        for (int i = 0; i < prefs.getInt("messageAmount", 0); i++) {
            if (prefs.getString("message" + i, "").equals(message)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

                    Toast.makeText(MainActivity.this, "The app has to save items to the external storage", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            System.out.println("Showing menu");
            PopupMenu menu = new PopupMenu(MainActivity.this, findViewById(R.id.deviceNameLayout), Gravity.CENTER_HORIZONTAL);
            menu.getMenuInflater().inflate(R.menu.more_options, menu.getMenu());
            menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == R.id.reset) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Confirm")
                                .setMessage("Continuing will reset current data.")
                                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        reset();

                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .create()
                                .show();
                    }
                    if (item.getItemId() == R.id.changeNum) {
                        alert();
                    }
                    if (item.getItemId() == R.id.resetPendingMessages) {
                        for(int i=0;i<pendingmessages.size();i++){
                            pendingmessages.remove(i);
                        }

                        SharedPreferences prefs = getSharedPreferences("pendingmessages", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("messageAmount", 0);
                        editor.apply();

                        //set pending messages number on ui
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ((TextView) ((RelativeLayout) findViewById(R.id.numberOfPendingMessagesLayout)).findViewById(R.id.numberOfPendingMessages)).setText(pendingmessages.size() + "");
                            }
                        });
                    }

                    if (item.getItemId() == R.id.changeTheme) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Confirm")
                                .setMessage("Continuing will reset current data.")
                                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(MainActivity.this, StartActivity.class);
                                        startActivity(intent);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .create()
                                .show();
                    }

                    if (item.getItemId() == R.id.restartBluetooth) {
                        restartListenerThread();
                    }

                    if (item.getItemId() == R.id.stopBluetooth) {
                        stopListenerThread();
                    }

                    Toast.makeText(MainActivity.this, item.getTitle(), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            menu.show();
        }
        return;
    }

    public void reset() {
        //setup scrolling viewpager
        alert();


        viewPager.setAdapter(pagerAdapter);

        PercentRelativeLayout layout;

        //Auto page
        layout = (PercentRelativeLayout) pagerAdapter.autoPage.getView().findViewById(R.id.autoPageLayout);
        clearData(layout);

        //Tele page
        pagerAdapter.teleopPage.reset();

        //Endgame page
        layout = (PercentRelativeLayout) pagerAdapter.endgamePage.getView().findViewById(R.id.endgamePageLayout);
        clearData(layout);
    }

    public void clearData(ViewGroup top) {
        for (int i = 0; i < top.getChildCount(); i++) {
            View v = top.getChildAt(i);
            if (v instanceof EditText) {
                ((EditText) v).setText("");
            }
            if (v instanceof CheckBox) {
                ((CheckBox) v).setChecked(false);
                v.jumpDrawablesToCurrentState();
            }
            if (v instanceof RadioGroup) {
                ((RadioGroup) v).clearCheck();
            }
            if (v instanceof RatingBar) {
                ((RatingBar) v).setRating(0);
            }
            if (v instanceof Spinner) {
                ((Spinner) v).setSelection(0);
            }
            if (v instanceof ViewGroup) {
                clearData((ViewGroup) v);
            }
        }
    }


    public void alert() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog)
                .setTitle("Enter Info")
                .setPositiveButton(android.R.string.yes, null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {

                //get date details
                final int year = Calendar.getInstance().get(Calendar.YEAR);
                final int month = Calendar.getInstance().get(Calendar.MONTH);
                final int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

                //setup spinners (Drop downs)
                View linearLayout = ((AlertDialog) dialog).findViewById(R.id.dialogLinearLayout);

                Spinner robotAlliance = (Spinner) linearLayout.findViewById(R.id.robotAlliance);

                ArrayAdapter<CharSequence> robotAllianceAdapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.alliances, R.layout.spinner);
                robotAlliance.setAdapter(robotAllianceAdapter);

                //make robot alliance disabled
                robotAlliance.setEnabled(false);

                //List user names available
                final Spinner userIDSpinner = (Spinner) linearLayout.findViewById(R.id.userID);

                //set a listener to make sure to adjust other fields based on it will change as well
                userIDSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        dialogScheduleDataChange(userIDSpinner, dialog);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });

                ArrayList<String> userNames = new ArrayList<>();
                userNames.add("Please choose a name");
                for (UserData userData : schedules){
                    userNames.add(userData.userName);
                }

                ArrayAdapter<String> userIDAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.spinner, userNames);
                userIDSpinner.setAdapter(userIDAdapter);
                //set to previous value
                SharedPreferences prefs = getSharedPreferences("userID", MODE_PRIVATE);
                userIDSpinner.setSelection(prefs.getInt("userID", 0));

                //Setup spinner for the side the field is being viewed from
                Spinner viewingSide = (Spinner) linearLayout.findViewById(R.id.viewingSide);

                ArrayAdapter<CharSequence> viewingSideAdapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.viewingSides, R.layout.spinner);
                viewingSide.setAdapter(viewingSideAdapter);

                //start bluetooth, all views are probably ready now
                startListenerThread();

                //set a listener for the match number as well to make sure to adjust other fields based on it will change as well
                ((EditText) linearLayout.findViewById(R.id.matchNumber)).addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        dialogScheduleDataChange(userIDSpinner, dialog);
                    }
                });

                //set spinners to previous values
                prefs = getSharedPreferences("robotAlliance", MODE_PRIVATE);
                if(prefs.getInt("day", -1) == day && prefs.getInt("month", -1) == month && prefs.getInt("year", -1) == year){
                    robotAlliance.setSelection(prefs.getInt("robotAlliance", 0));
                }

                prefs = getSharedPreferences("viewingSide", MODE_PRIVATE);
                if(prefs.getInt("day", -1) == day && prefs.getInt("month", -1) == month && prefs.getInt("year", -1) == year){
                    viewingSide.setSelection(prefs.getInt("viewingSide", 0));
                }

                //once they hit ok
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onClickOkButton(dialog, false);
                    }
                });

            }
        });
        dialog.show();
    }

    //When the user ID is changed by the userID spinner changing or when the match number is changed
    public void dialogScheduleDataChange(Spinner userIDSpinner, DialogInterface dialog) {
        int newUserID = userIDSpinner.getSelectedItemPosition() - 1;

        //it's moved to the default, no need to change anything
        if(newUserID == -1) {
            return;
        }

        //has it been 15 minutes
        if (newUserID != userID && (lastSubmit == -1 || System.currentTimeMillis() - lastSubmit > 900000)) {
            //make a confirmation message here
            AlertDialog confirmationDialog = new AlertDialog.Builder(this)
                    .setTitle("Are you still " + schedules.get(MainActivity.this.userID).userName + "?")
                    .setMessage("If you are not " + schedules.get(MainActivity.this.userID).userName + ", make sure to change the user.\n\n" +
                            "Make sure the match number is accurate as well")
                    .setPositiveButton(android.R.string.yes, null)
                    .setCancelable(true)
                    .create();

            lastSubmit = System.currentTimeMillis();
        }

        //change other buttons on the dialog box accordingly
        View linearLayout = ((AlertDialog) dialog).findViewById(R.id.dialogLinearLayout);

        EditText roundInput = (EditText) linearLayout.findViewById(R.id.matchNumber);

        String roundText = roundInput.getText().toString();
        if (roundText.equals("")) {
            //The user has not specified what match number it is yet
            runOnUiThread(new Thread() {
                public void run() {
                    Toast.makeText(MainActivity.this, "You must specify the match number",
                            Toast.LENGTH_SHORT).show();
                }
            });

            return;
        }
        //if the match number has been selected it can be used
        int round = Integer.parseInt(roundText) - 1;

        EditText robotNumInput = (EditText) linearLayout.findViewById(R.id.robotNumber);
        Spinner robotAlliance = (Spinner) linearLayout.findViewById(R.id.robotAlliance);

        if (round >= schedules.get(MainActivity.this.userID).robots.size() || round < 0){
            //The match number is too high
            runOnUiThread(new Thread() {
                public void run() {
                    Toast.makeText(MainActivity.this, "This match number is not on the schedule yet, choose another",
                            Toast.LENGTH_SHORT).show();
                }
            });

            //set the robot number to blank
            robotNumInput.setText("");
            //reset alliance
            robotAlliance.setSelection(0);
            return;
        }

        //find the robot alliance
        alliance = schedules.get(MainActivity.this.userID).alliances.get(round);

        robotNumInput.setText(String.valueOf(schedules.get(MainActivity.this.userID).robots.get(round)));

        if (alliance) {
            robotAlliance.setSelection(1);
        } else {
            robotAlliance.setSelection(2);
        }

    }

    //for the alert
    public void onClickOkButton(DialogInterface dialog, boolean overrideRobotNumberCheck){
        //get date details
        final int year = Calendar.getInstance().get(Calendar.YEAR);
        final int month = Calendar.getInstance().get(Calendar.MONTH);
        final int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.dialog, null);

        View linearLayout = ((AlertDialog) dialog).findViewById(R.id.dialogLinearLayout);

        EditText robotNumInput = (EditText) linearLayout.findViewById(R.id.robotNumber);
        EditText roundInput = (EditText) linearLayout.findViewById(R.id.matchNumber);

        //spinners
        Spinner robotAlliance = (Spinner) linearLayout.findViewById(R.id.robotAlliance);
        Spinner viewingSide = (Spinner) linearLayout.findViewById(R.id.viewingSide);
        Spinner userID = (Spinner) linearLayout.findViewById(R.id.userID);

        if(robotAlliance.getSelectedItemPosition() == 0 || viewingSide.getSelectedItemPosition() == 0){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Please select which side you are on, and what alliance your robot is on...",
                            Toast.LENGTH_LONG).show();
                }
            });

            return;
        }

        try {
            robotNum = Integer.parseInt(robotNumInput.getText().toString());
            round = Integer.parseInt(roundInput.getText().toString());

            alliance = robotAlliance.getSelectedItemPosition() == 2;
            side = viewingSide.getSelectedItemPosition() == 2;

            //adjust the field image according to selection
            pagerAdapter.teleopPage.field.switchSides(side);

            SharedPreferences prefs = getSharedPreferences("userID", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("userID", userID.getSelectedItemPosition());
            editor.apply();

            //save selections for robot alliance
            prefs = getSharedPreferences("robotAlliance", MODE_PRIVATE);
            editor = prefs.edit();
            editor.putInt("robotAlliance", robotAlliance.getSelectedItemPosition());
            editor.putInt("year", year);
            editor.putInt("month", month);
            editor.putInt("day", day);
            editor.apply();

            //save selections for seating placement
            prefs = getSharedPreferences("viewingSide", MODE_PRIVATE);
            editor = prefs.edit();
            editor.putInt("viewingSide", viewingSide.getSelectedItemPosition());
            editor.putInt("year", year);
            editor.putInt("month", month);
            editor.putInt("day", day);
            editor.apply();

            if (round > 99) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Invalid Match Number",
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            //the list contains spaces at the end of each line so a space is added to the search
            if (!arrayContains(availableRobots, robotNum + "") && !overrideRobotNumberCheck) {
                createRobotNumberOverrideDialog(dialog);
                return;
            }

            if (userID.getSelectedItemPosition() == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Please choose a user",
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

        } catch (NumberFormatException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Invalid Data! Are any fields blank?",
                            Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        robotNumText = (TextView) findViewById(R.id.robotNum);
        robotNumText.setText("Robot: " + robotNum + " " + "Round: " + round);

        dialog.dismiss();
    }

    public boolean arrayContains(String[] array, String search){
        for(String string : array){
            System.out.println(string + "vs" + search);
            System.out.println(string.length() + "vs" + search.length());
            if(string.equals(search)){
                return true;
            }
        }
        return false;
    }

    //creates dialog to check if the user still wants to use a robot not in this event
    public void createRobotNumberOverrideDialog(final DialogInterface dialog){
        new AlertDialog.Builder(this)
                .setTitle("That robot is not at this event")
                .setMessage("Would you like to use this robot number anyway? DOUBLE CHECK that you are typing in the right robot number.")
                .setPositiveButton("Yes, I would like to use this robot number", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog2, int which) {
                        onClickOkButton(dialog, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

     public static void startNotificationAlarm(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent (context, PendingNotification.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
        Date date = Calendar.getInstance().getTime();
        System.out.println(date.toString());
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+1000*60*20, pending);
    }

}
