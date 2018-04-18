package sahmed.dburkhart.com.taplogger;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Main Activity class that contains experiment
 */
public class MainActivity extends AppCompatActivity {

    private int btnClicks[] = new int[110];

    //Firebase stuff
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference myDatabaseReference = database.getReference();
    private StorageReference myStorageReference = FirebaseStorage.getInstance().getReference();
    private FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private FirebaseUser user;
    private DatabaseReference userIDReference;

    private String uid = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
    private int btnID = -1;

    //Sensor stuff
    private SensorManager mySensorManager;
    private SensorEventListener mySensorListener;

    //File Writing Stuff
    private File file;
    private FileOutputStream fOut;
    private OutputStreamWriter writer;
    private long timestamp;

    //Sensor data holders
    private float[] lastGyroscopeValues = {0, 0, 0};
    private float[] lastAccelerometerValues = {0, 0, 0};
    private float[] lastRotationVectorValues = {0, 0, 0};

    private final int FILE_CODE = 2222;

    private ArrayList<Button> buttonsArrayList = new ArrayList<Button>();

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (this.firebaseAuth.getCurrentUser() == null) {

            this.firebaseAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                Log.d("success", "Sign in successful");
                                user = firebaseAuth.getCurrentUser();
                            } else {
                                Log.w("TAG", "signInAnonymously:failure", task.getException());
                                Toast.makeText(MainActivity.this, "Authentication failed.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            this.firebaseAuth.getCurrentUser().reload();
        }

        if (this.firebaseAuth.getCurrentUser() != null) {
            this.user = firebaseAuth.getCurrentUser();
            this.userIDReference = this.myDatabaseReference.child(this.user.getUid());
        }

        if (this.permissionNotGranted()) {
            this.getPermission();
        }

        this.setUpSensors();
        this.setUpButtons();
        this.setClickListenerForButtons();
        this.setTouchListenerForButtons();
        this.setUpFileOutput();
    }

    /**
     * Checks permissions to read and write to external storage and requests them if not granted already
     */
    private void getPermission() {
        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        }
    }


    /**
     * Sets up the sensors used in this project with sensor managers and sensor listeners
     */
    private void setUpSensors() {
        this.mySensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        this.mySensorListener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor arg0, int arg1) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;
                if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    lastGyroscopeValues = event.values;
                } else if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    lastAccelerometerValues = event.values;
                } else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    lastRotationVectorValues = event.values;
                }
                timestamp = SystemClock.uptimeMillis();
                capture(sensor.getName());
            }
        };

        this.mySensorManager.registerListener(this.mySensorListener,
                this.mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);

        this.mySensorManager.registerListener(this.mySensorListener,
                this.mySensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);

        this.mySensorManager.registerListener(this.mySensorListener,
                this.mySensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_GAME);
    }

    /**
     * Sets up the output file and registers the sensors
     */
    private void setUpFileOutput() {

        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        } else {

            String fileName = new SimpleDateFormat("yyyyMMddHHmm'_recording.csv'").format(new Date());

            try {

                if (this.isExternalStorageReadable() && this.isExternalStorageWritable()) {

                    String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                    File newDir = new File(root + "/SensorReadings");
                    boolean result = newDir.mkdirs();

                    this.file = new File(newDir, fileName);
                    this.fOut = new FileOutputStream(this.file);
                    this.writer = new OutputStreamWriter(this.fOut);
                    this.writer.append("timestamp,sensorName," +
                            "lastAccelerometerValues[0],lastAccelerometerValues[1],lastAccelerometerValues[2]," +
                            "lastGyroscopeValues[0],lastGyroscopeValues[1],lastGyroscopeValues[2]," +
                            "lastRotationVectorValues[0],lastRotationVectorValues[1],lastRotationVectorValues[2]," +
                            "lastBtnId\n");

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets the onClick listener for the 108 buttons
     */
    private void setClickListenerForButtons() {
        for (Button currButton : this.buttonsArrayList) {
            currButton.setOnClickListener(this.getOnclickListener(currButton));
        }
    }

    /**
     * Sets the onTouch listener for the 108 buttons
     */
    private void setTouchListenerForButtons() {
        for (Button currButton : this.buttonsArrayList) {
            currButton.setOnTouchListener(this.getOnTouchListener(currButton));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (this.mySensorListener != null) {
            this.mySensorManager.unregisterListener(this.mySensorListener);
        }

        if (this.fOut != null && this.writer != null) {
            try {
                this.writer.close();
                this.fOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (this.file != null && this.user != null && this.uid != null) {
            this.myStorageReference.child(this.user.getUid()).child(this.uid).putFile(Uri.fromFile(this.file));
        }
    }

    /**
     * Creates onClick Listener for button
     *
     * @param currButton The current button
     * @return An onClick Listener event to be used by the button during this experiment
     */
    private View.OnClickListener getOnclickListener(final Button currButton) {

        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int btnViewID = v.getId() - buttonsArrayList.get(0).getId();
                btnID = Integer.parseInt(currButton.getText().toString().trim());
                btnClicks[btnViewID]++;

                if (userIDReference != null) {
                    userIDReference.child("btn").child(Integer.toString(btnViewID));
                    userIDReference.child("btnClicks").child(Integer.toString(btnClicks[btnViewID]));
                }

                switch (btnClicks[btnViewID]) {
                    case 1: {
                        v.setBackgroundColor(Color.BLUE);
                        break;
                    }
                    case 2: {
                        v.setBackgroundColor(Color.YELLOW);
                        break;
                    }
                    case 3: {
                        v.setBackgroundColor(Color.GREEN);
                        break;
                    }
                    case 4: {
                        v.setBackgroundColor(Color.RED);
                        break;
                    }
                    case 5: {
                        v.setBackgroundColor(Color.CYAN);
                        break;
                    }
                    case 6: {
                        v.setBackgroundColor(Color.LTGRAY);
                        break;
                    }
                    case 7: {
                        v.setBackgroundColor(Color.MAGENTA);
                        break;
                    }
                    case 8: {
                        v.setBackgroundColor(Color.DKGRAY);
                        break;
                    }
                    case 9: {
                        v.setBackgroundColor(Color.WHITE);
                        break;
                    }
                    case 10: {
                        v.setVisibility(View.INVISIBLE);
                        break;
                    }
                }
                Log.d("btnID", Integer.toString(btnID));
            }
        };
    }

    /**
     * Creates onTouch Listener for button
     *
     * @param currButton The current button
     * @return An onTouch Listener event to be used by the button during this experiment
     */
    private View.OnTouchListener getOnTouchListener(final Button currButton) {

        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int btnViewID = v.getId() - buttonsArrayList.get(0).getId();

                btnID = Integer.parseInt(currButton.getText().toString().trim());

                if (userIDReference != null) {
                    userIDReference.child(uid).child("btnID").child(Integer.toString(btnID))
                            .child(Long.toString(SystemClock.uptimeMillis()))
                            .child(Integer.toString(btnClicks[btnViewID]))
                            .child(Integer.toString(event.getAction()))
                            .setValue(event.toString());
                }

                Log.d("btnID", Integer.toString(btnID));
                return false;
            }
        };
    }

    /**
     * Captures the current sensor state and writes it to the file
     *
     * @param sensorName The Name of the sensor.
     */
    public void capture(String sensorName) {

        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        } else {
            try {
                writer.append(String.valueOf(this.timestamp))
                        .append(",").append(sensorName)
                        .append(",").append(String.valueOf(this.lastAccelerometerValues[0]))
                        .append(",").append(String.valueOf(this.lastAccelerometerValues[1]))
                        .append(",").append(String.valueOf(this.lastAccelerometerValues[2]))
                        .append(",").append(String.valueOf(this.lastGyroscopeValues[0]))
                        .append(",").append(String.valueOf(this.lastGyroscopeValues[1]))
                        .append(",").append(String.valueOf(this.lastGyroscopeValues[2]))
                        .append(",").append(String.valueOf(this.lastRotationVectorValues[0]))
                        .append(",").append(String.valueOf(this.lastRotationVectorValues[1]))
                        .append(",").append(String.valueOf(this.lastRotationVectorValues[2]))
                        .append(",").append(String.valueOf(this.btnID)).append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets up all the buttons from the view, and adds them to an arraylist for iteration
     */
    private void setUpButtons() {
        final Button btn1 = findViewById(R.id.button1);
        Button btn2 = findViewById(R.id.button2);
        Button btn3 = findViewById(R.id.button3);
        Button btn4 = findViewById(R.id.button4);
        Button btn5 = findViewById(R.id.button5);
        Button btn6 = findViewById(R.id.button6);
        Button btn7 = findViewById(R.id.button7);
        Button btn8 = findViewById(R.id.button8);
        Button btn9 = findViewById(R.id.button9);

        Button btn10 = findViewById(R.id.button10);
        Button btn11 = findViewById(R.id.button11);
        Button btn12 = findViewById(R.id.button12);
        Button btn13 = findViewById(R.id.button13);
        Button btn14 = findViewById(R.id.button14);
        Button btn15 = findViewById(R.id.button15);
        Button btn16 = findViewById(R.id.button16);
        Button btn17 = findViewById(R.id.button17);
        Button btn18 = findViewById(R.id.button18);
        Button btn19 = findViewById(R.id.button19);

        Button btn20 = findViewById(R.id.button20);
        Button btn21 = findViewById(R.id.button21);
        Button btn22 = findViewById(R.id.button22);
        Button btn23 = findViewById(R.id.button23);
        Button btn24 = findViewById(R.id.button24);
        Button btn25 = findViewById(R.id.button25);
        Button btn26 = findViewById(R.id.button26);
        Button btn27 = findViewById(R.id.button27);
        Button btn28 = findViewById(R.id.button28);
        Button btn29 = findViewById(R.id.button29);

        Button btn30 = findViewById(R.id.button30);
        Button btn31 = findViewById(R.id.button31);
        Button btn32 = findViewById(R.id.button32);
        Button btn33 = findViewById(R.id.button33);
        Button btn34 = findViewById(R.id.button34);
        Button btn35 = findViewById(R.id.button35);
        Button btn36 = findViewById(R.id.button36);
        Button btn37 = findViewById(R.id.button37);
        Button btn38 = findViewById(R.id.button38);
        Button btn39 = findViewById(R.id.button39);

        Button btn40 = findViewById(R.id.button40);
        Button btn41 = findViewById(R.id.button41);
        Button btn42 = findViewById(R.id.button42);
        Button btn43 = findViewById(R.id.button43);
        Button btn44 = findViewById(R.id.button44);
        Button btn45 = findViewById(R.id.button45);
        Button btn46 = findViewById(R.id.button46);
        Button btn47 = findViewById(R.id.button47);
        Button btn48 = findViewById(R.id.button48);
        Button btn49 = findViewById(R.id.button49);

        Button btn50 = findViewById(R.id.button50);
        Button btn51 = findViewById(R.id.button51);
        Button btn52 = findViewById(R.id.button52);
        Button btn53 = findViewById(R.id.button53);
        Button btn54 = findViewById(R.id.button54);
        Button btn55 = findViewById(R.id.button55);
        Button btn56 = findViewById(R.id.button56);
        Button btn57 = findViewById(R.id.button57);
        Button btn58 = findViewById(R.id.button58);
        Button btn59 = findViewById(R.id.button59);

        Button btn60 = findViewById(R.id.button60);
        Button btn61 = findViewById(R.id.button61);
        Button btn62 = findViewById(R.id.button62);
        Button btn63 = findViewById(R.id.button63);
        Button btn64 = findViewById(R.id.button64);
        Button btn65 = findViewById(R.id.button65);
        Button btn66 = findViewById(R.id.button66);
        Button btn67 = findViewById(R.id.button67);
        Button btn68 = findViewById(R.id.button68);
        Button btn69 = findViewById(R.id.button69);

        Button btn70 = findViewById(R.id.button70);
        Button btn71 = findViewById(R.id.button71);
        Button btn72 = findViewById(R.id.button72);
        Button btn73 = findViewById(R.id.button73);
        Button btn74 = findViewById(R.id.button74);
        Button btn75 = findViewById(R.id.button75);
        Button btn76 = findViewById(R.id.button76);
        Button btn77 = findViewById(R.id.button77);
        Button btn78 = findViewById(R.id.button78);
        Button btn79 = findViewById(R.id.button79);

        Button btn80 = findViewById(R.id.button80);
        Button btn81 = findViewById(R.id.button81);
        Button btn82 = findViewById(R.id.button82);
        Button btn83 = findViewById(R.id.button83);
        Button btn84 = findViewById(R.id.button84);
        Button btn85 = findViewById(R.id.button85);
        Button btn86 = findViewById(R.id.button86);
        Button btn87 = findViewById(R.id.button87);
        Button btn88 = findViewById(R.id.button88);
        Button btn89 = findViewById(R.id.button89);

        Button btn90 = findViewById(R.id.button90);
        Button btn91 = findViewById(R.id.button91);
        Button btn92 = findViewById(R.id.button92);
        Button btn93 = findViewById(R.id.button93);
        Button btn94 = findViewById(R.id.button94);
        Button btn95 = findViewById(R.id.button95);
        Button btn96 = findViewById(R.id.button96);
        Button btn97 = findViewById(R.id.button97);
        Button btn98 = findViewById(R.id.button98);
        Button btn99 = findViewById(R.id.button99);

        Button btn100 = findViewById(R.id.button100);
        Button btn101 = findViewById(R.id.button101);
        Button btn102 = findViewById(R.id.button102);
        Button btn103 = findViewById(R.id.button103);
        Button btn104 = findViewById(R.id.button104);
        Button btn105 = findViewById(R.id.button105);
        Button btn106 = findViewById(R.id.button106);
        Button btn107 = findViewById(R.id.button107);
        Button btn108 = findViewById(R.id.button108);

        this.buttonsArrayList = new ArrayList<Button>();
        this.buttonsArrayList.add(btn1);
        this.buttonsArrayList.add(btn2);
        this.buttonsArrayList.add(btn3);
        this.buttonsArrayList.add(btn4);
        this.buttonsArrayList.add(btn5);
        this.buttonsArrayList.add(btn6);
        this.buttonsArrayList.add(btn7);
        this.buttonsArrayList.add(btn8);
        this.buttonsArrayList.add(btn9);
        this.buttonsArrayList.add(btn10);

        this.buttonsArrayList.add(btn11);
        this.buttonsArrayList.add(btn12);
        this.buttonsArrayList.add(btn13);
        this.buttonsArrayList.add(btn14);
        this.buttonsArrayList.add(btn15);
        this.buttonsArrayList.add(btn16);
        this.buttonsArrayList.add(btn17);
        this.buttonsArrayList.add(btn18);
        this.buttonsArrayList.add(btn19);
        this.buttonsArrayList.add(btn20);

        this.buttonsArrayList.add(btn21);
        this.buttonsArrayList.add(btn22);
        this.buttonsArrayList.add(btn23);
        this.buttonsArrayList.add(btn24);
        this.buttonsArrayList.add(btn25);
        this.buttonsArrayList.add(btn26);
        this.buttonsArrayList.add(btn27);
        this.buttonsArrayList.add(btn28);
        this.buttonsArrayList.add(btn29);

        this.buttonsArrayList.add(btn30);
        this.buttonsArrayList.add(btn31);
        this.buttonsArrayList.add(btn32);
        this.buttonsArrayList.add(btn33);
        this.buttonsArrayList.add(btn34);
        this.buttonsArrayList.add(btn35);
        this.buttonsArrayList.add(btn36);
        this.buttonsArrayList.add(btn37);
        this.buttonsArrayList.add(btn38);
        this.buttonsArrayList.add(btn39);

        this.buttonsArrayList.add(btn40);
        this.buttonsArrayList.add(btn41);
        this.buttonsArrayList.add(btn42);
        this.buttonsArrayList.add(btn43);
        this.buttonsArrayList.add(btn44);
        this.buttonsArrayList.add(btn45);
        this.buttonsArrayList.add(btn46);
        this.buttonsArrayList.add(btn47);
        this.buttonsArrayList.add(btn48);
        this.buttonsArrayList.add(btn49);

        this.buttonsArrayList.add(btn50);
        this.buttonsArrayList.add(btn51);
        this.buttonsArrayList.add(btn52);
        this.buttonsArrayList.add(btn53);
        this.buttonsArrayList.add(btn54);
        this.buttonsArrayList.add(btn55);
        this.buttonsArrayList.add(btn56);
        this.buttonsArrayList.add(btn57);
        this.buttonsArrayList.add(btn58);
        this.buttonsArrayList.add(btn59);

        this.buttonsArrayList.add(btn60);
        this.buttonsArrayList.add(btn61);
        this.buttonsArrayList.add(btn62);
        this.buttonsArrayList.add(btn63);
        this.buttonsArrayList.add(btn64);
        this.buttonsArrayList.add(btn65);
        this.buttonsArrayList.add(btn66);
        this.buttonsArrayList.add(btn67);
        this.buttonsArrayList.add(btn68);
        this.buttonsArrayList.add(btn69);

        this.buttonsArrayList.add(btn70);
        this.buttonsArrayList.add(btn71);
        this.buttonsArrayList.add(btn72);
        this.buttonsArrayList.add(btn73);
        this.buttonsArrayList.add(btn74);
        this.buttonsArrayList.add(btn75);
        this.buttonsArrayList.add(btn76);
        this.buttonsArrayList.add(btn77);
        this.buttonsArrayList.add(btn78);
        this.buttonsArrayList.add(btn79);

        this.buttonsArrayList.add(btn80);
        this.buttonsArrayList.add(btn81);
        this.buttonsArrayList.add(btn82);
        this.buttonsArrayList.add(btn83);
        this.buttonsArrayList.add(btn84);
        this.buttonsArrayList.add(btn85);
        this.buttonsArrayList.add(btn86);
        this.buttonsArrayList.add(btn87);
        this.buttonsArrayList.add(btn88);
        this.buttonsArrayList.add(btn89);

        this.buttonsArrayList.add(btn90);
        this.buttonsArrayList.add(btn91);
        this.buttonsArrayList.add(btn92);
        this.buttonsArrayList.add(btn93);
        this.buttonsArrayList.add(btn94);
        this.buttonsArrayList.add(btn95);
        this.buttonsArrayList.add(btn96);
        this.buttonsArrayList.add(btn97);
        this.buttonsArrayList.add(btn98);
        this.buttonsArrayList.add(btn99);

        this.buttonsArrayList.add(btn100);
        this.buttonsArrayList.add(btn101);
        this.buttonsArrayList.add(btn102);
        this.buttonsArrayList.add(btn103);
        this.buttonsArrayList.add(btn104);
        this.buttonsArrayList.add(btn105);
        this.buttonsArrayList.add(btn106);
        this.buttonsArrayList.add(btn107);
        this.buttonsArrayList.add(btn108);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {

            case FILE_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else if (permissionNotGranted()) {
                    Log.d("ACCESS", "You don't have permission to access storage");
                    System.exit(0);
                }
                return;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Checks files permissions
     *
     * @return True if permissions have been granted, false otherwise
     */
    private boolean permissionNotGranted() {
        return (ActivityCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                && (ActivityCompat.checkSelfPermission(this, permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED);
    }


    /**
     * Checks if external storage is writable
     *
     * @return True if media is writeable, false otherwise
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * Checks if external storage is readable
     *
     * @return True if media is read only or Read/write, false otherwise
     */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

}
