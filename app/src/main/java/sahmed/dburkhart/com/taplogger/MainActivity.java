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
import com.google.firebase.FirebaseApp;
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

public class MainActivity extends AppCompatActivity {

    private int clicks[] = new int[100];

    //Firebase database stuff
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference myDatabaseReference = database.getReference();
    private StorageReference mStorageRef;
    private FirebaseAuth firebaseAuth;
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

    private ArrayList<Button> buttonsArrayList = new ArrayList<>();

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.getPermission();

        FirebaseApp app = FirebaseApp.getInstance();
        this.mStorageRef = FirebaseStorage.getInstance().getReference();
        this.firebaseAuth = FirebaseAuth.getInstance(app);

        if (this.firebaseAuth.getCurrentUser() != null) {
            this.firebaseAuth.getCurrentUser().reload();
        } else {

            this.firebaseAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                Log.d("success", "Sign in successful");
                                // Sign in success, update UI with the signed-in user's information
                                //   Log.d("TAG", "signInAnonymously:success");
                                user = firebaseAuth.getCurrentUser();
                            } else {
                                // If sign in fails, display a message to the user.
                                Log.w("TAG", "signInAnonymously:failure", task.getException());
                                Toast.makeText(MainActivity.this, "Authentication failed.",
                                        Toast.LENGTH_SHORT).show();
                            }

                        }
                    });
        }

        if (this.firebaseAuth.getCurrentUser() != null) {
            this.user = firebaseAuth.getCurrentUser();
            System.out.println("UID: " + this.user.getUid());
            this.userIDReference = this.myDatabaseReference.child(this.user.getUid());

            this.setUpSensors();
            this.setUpFileOutput();
            this.setUpButtons();
            this.setClickListenerForButtons();
            this.setTouchListenerForButtons();
        }

    }

    private void getPermission() {
        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        }
    }


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
                snapshot(sensor.getName());
            }
        };
    }

    private void setUpFileOutput() {

        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        } else {

            String fileName = new SimpleDateFormat("yyyyMMddHHmm'_recording.csv'").format(new Date());

            try {

                System.out.println(this.isExternalStorageReadable() && this.isExternalStorageWritable());


                if (this.isExternalStorageWritable() && this.isExternalStorageReadable()) {

                    String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

                    System.out.println("ROOT: " + root);

                    File newDir = new File(root + "/SensorReadings");
                    boolean firstResult = newDir.mkdir();
                    boolean result = newDir.mkdirs();

                    System.out.println("MKDIR result: " + result);

                    this.file = new File(newDir, fileName);

                    this.fOut = new FileOutputStream(this.file);
                    this.writer = new OutputStreamWriter(this.fOut);
                    this.writer.append("timestamp, sensorName, " +
                            "lastAccelerometerValues[0], lastAccelerometerValues[1], lastAccelerometerValues[2], " +
                            "lastGyroscopeValues[0], lastGyroscopeValues[1], lastGyroscopeValues[2]," +
                            " lastRotationVectorValues[0], lastRotationVectorValues[1], lastRotationVectorValues[2], " +
                            "lastBtnId\n");

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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setClickListenerForButtons() {
        for (Button currButton : this.buttonsArrayList) {
            currButton.setOnClickListener(this.getOnclickListener(currButton));
        }
    }

    private void setTouchListenerForButtons() {
        for (Button currButton : this.buttonsArrayList) {
            currButton.setOnTouchListener(this.getOnTouchListener(currButton));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        this.mySensorManager.unregisterListener(this.mySensorListener);

        if (this.fOut != null && this.writer != null) {
            try {
                this.writer.close();
                this.fOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Uri fileURI = Uri.fromFile(this.file);
        StorageReference childRef = this.mStorageRef.child(this.user.getUid()).child(this.uid);
        childRef.putFile(fileURI);
    }

    private View.OnClickListener getOnclickListener(final Button currButton) {

        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = v.getId() - buttonsArrayList.get(0).getId();

                btnID = Integer.parseInt(currButton.getText().toString().trim());

                clicks[i]++;
                userIDReference.child("btn").child(Integer.toString(i));
                userIDReference.child("clicks").child(Integer.toString(clicks[i]));

                switch (clicks[i]) {
                    case 1:
                        v.setBackgroundColor(Color.BLUE);
                        break;
                    case 2:
                        v.setBackgroundColor(Color.YELLOW);
                        break;
                    case 3:
                        v.setBackgroundColor(Color.GREEN);
                        break;
                    case 4:
                        v.setBackgroundColor(Color.RED);
                        break;
                    case 5:
                        v.setBackgroundColor(Color.CYAN);
                        break;
                    case 6:
                        v.setBackgroundColor(Color.LTGRAY);
                        break;
                    case 7:
                        v.setBackgroundColor(Color.MAGENTA);
                        break;
                    case 8:
                        v.setBackgroundColor(Color.DKGRAY);
                        break;
                    case 9:
                        v.setBackgroundColor(Color.WHITE);
                        break;
                    case 10:
                        v.setVisibility(View.INVISIBLE);
                }

                Log.d("btnID", Integer.toString(btnID));
            }
        };
    }

    private View.OnTouchListener getOnTouchListener(final Button currButton) {

        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int i = v.getId() - buttonsArrayList.get(0).getId();

                btnID = Integer.parseInt(currButton.getText().toString().trim());

                userIDReference.child(uid).child("btnID").child(Integer.toString(btnID))
                        .child(Long.toString(SystemClock.uptimeMillis()))
                        .child(Integer.toString(clicks[i]))
                        .child(Integer.toString(event.getAction()))
                        .setValue(event.toString());

                Log.d("btnID", Integer.toString(btnID));

                return false;
            }
        };
    }

    public void snapshot(String sensorName) {

        if (ContextCompat.checkSelfPermission(this, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE},
                    FILE_CODE);
        } else {

            try {
                writer.append(String.valueOf(timestamp))
                        .append(",").append(sensorName)
                        .append(",").append(String.valueOf(lastAccelerometerValues[0]))
                        .append(",").append(String.valueOf(lastAccelerometerValues[1]))
                        .append(",").append(String.valueOf(lastAccelerometerValues[2]))
                        .append(",").append(String.valueOf(lastGyroscopeValues[0]))
                        .append(",").append(String.valueOf(lastGyroscopeValues[1]))
                        .append(",").append(String.valueOf(lastGyroscopeValues[2]))
                        .append(",").append(String.valueOf(lastRotationVectorValues[0]))
                        .append(",").append(String.valueOf(lastRotationVectorValues[1]))
                        .append(",").append(String.valueOf(lastRotationVectorValues[2]))
                        .append(",").append(String.valueOf(btnID)).append("\n");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setUpButtons() {
        final Button bt001 = findViewById(R.id.button1);
        Button bt002 = findViewById(R.id.button2);
        Button bt003 = findViewById(R.id.button3);
        Button bt004 = findViewById(R.id.button4);
        Button bt005 = findViewById(R.id.button5);
        Button bt006 = findViewById(R.id.button6);
        Button bt007 = findViewById(R.id.button7);
        Button bt008 = findViewById(R.id.button8);
        Button bt009 = findViewById(R.id.button9);
        Button bt010 = findViewById(R.id.button10);
        Button bt011 = findViewById(R.id.button11);
        Button bt012 = findViewById(R.id.button12);
        Button bt013 = findViewById(R.id.button13);
        Button bt014 = findViewById(R.id.button14);
        Button bt015 = findViewById(R.id.button15);
        Button bt016 = findViewById(R.id.button16);
        Button bt017 = findViewById(R.id.button17);
        Button bt018 = findViewById(R.id.button18);
        Button bt019 = findViewById(R.id.button19);
        Button bt020 = findViewById(R.id.button20);
        Button bt021 = findViewById(R.id.button21);
        Button bt022 = findViewById(R.id.button22);
        Button bt023 = findViewById(R.id.button23);
        Button bt024 = findViewById(R.id.button24);
        Button bt025 = findViewById(R.id.button25);
        Button bt026 = findViewById(R.id.button26);
        Button bt027 = findViewById(R.id.button27);
        Button bt028 = findViewById(R.id.button28);
        Button bt029 = findViewById(R.id.button29);
        Button bt030 = findViewById(R.id.button30);
        Button bt031 = findViewById(R.id.button31);
        Button bt032 = findViewById(R.id.button32);
        Button bt033 = findViewById(R.id.button33);
        Button bt034 = findViewById(R.id.button34);
        Button bt035 = findViewById(R.id.button35);
        Button bt036 = findViewById(R.id.button36);
        Button bt037 = findViewById(R.id.button37);
        Button bt038 = findViewById(R.id.button38);
        Button bt039 = findViewById(R.id.button39);
        Button bt040 = findViewById(R.id.button40);
        Button bt041 = findViewById(R.id.button41);
        Button bt042 = findViewById(R.id.button42);
        Button bt043 = findViewById(R.id.button43);
        Button bt044 = findViewById(R.id.button44);
        Button bt045 = findViewById(R.id.button45);
        Button bt046 = findViewById(R.id.button46);
        Button bt047 = findViewById(R.id.button47);
        Button bt048 = findViewById(R.id.button48);
        Button bt049 = findViewById(R.id.button49);
        Button bt050 = findViewById(R.id.button50);
        Button bt051 = findViewById(R.id.button51);
        Button bt052 = findViewById(R.id.button52);
        Button bt053 = findViewById(R.id.button53);
        Button bt054 = findViewById(R.id.button54);
        Button bt055 = findViewById(R.id.button55);
        Button bt056 = findViewById(R.id.button56);
        Button bt057 = findViewById(R.id.button57);
        Button bt058 = findViewById(R.id.button58);
        Button bt059 = findViewById(R.id.button59);
        Button bt060 = findViewById(R.id.button60);
        Button bt061 = findViewById(R.id.button61);
        Button bt062 = findViewById(R.id.button62);
        Button bt063 = findViewById(R.id.button63);
        Button bt064 = findViewById(R.id.button64);
        Button bt065 = findViewById(R.id.button65);
        Button bt066 = findViewById(R.id.button66);
        Button bt067 = findViewById(R.id.button67);
        Button bt068 = findViewById(R.id.button68);
        Button bt069 = findViewById(R.id.button69);
        Button bt070 = findViewById(R.id.button70);
        Button bt071 = findViewById(R.id.button71);
        Button bt072 = findViewById(R.id.button72);
        Button bt073 = findViewById(R.id.button73);
        Button bt074 = findViewById(R.id.button74);
        Button bt075 = findViewById(R.id.button75);
        Button bt076 = findViewById(R.id.button76);
        Button bt077 = findViewById(R.id.button77);
        Button bt078 = findViewById(R.id.button78);
        Button bt079 = findViewById(R.id.button79);
        Button bt080 = findViewById(R.id.button80);
        Button bt081 = findViewById(R.id.button81);
        Button bt082 = findViewById(R.id.button82);
        Button bt083 = findViewById(R.id.button83);
        Button bt084 = findViewById(R.id.button84);
        Button bt085 = findViewById(R.id.button85);
        Button bt086 = findViewById(R.id.button86);
        Button bt087 = findViewById(R.id.button87);
        Button bt088 = findViewById(R.id.button88);
        Button bt089 = findViewById(R.id.button89);
        Button bt090 = findViewById(R.id.button90);
        Button bt091 = findViewById(R.id.button91);
        Button bt092 = findViewById(R.id.button92);
        Button bt093 = findViewById(R.id.button93);
        Button bt094 = findViewById(R.id.button94);
        Button bt095 = findViewById(R.id.button95);
        Button bt096 = findViewById(R.id.button96);
        Button bt097 = findViewById(R.id.button97);
        Button bt098 = findViewById(R.id.button98);
        Button bt099 = findViewById(R.id.button99);


        this.buttonsArrayList = new ArrayList<Button>();
        this.buttonsArrayList.add(bt001);
        this.buttonsArrayList.add(bt002);
        this.buttonsArrayList.add(bt003);
        this.buttonsArrayList.add(bt004);
        this.buttonsArrayList.add(bt005);
        this.buttonsArrayList.add(bt006);
        this.buttonsArrayList.add(bt007);
        this.buttonsArrayList.add(bt008);
        this.buttonsArrayList.add(bt009);
        this.buttonsArrayList.add(bt010);

        this.buttonsArrayList.add(bt011);
        this.buttonsArrayList.add(bt012);
        this.buttonsArrayList.add(bt013);
        this.buttonsArrayList.add(bt014);
        this.buttonsArrayList.add(bt015);
        this.buttonsArrayList.add(bt016);
        this.buttonsArrayList.add(bt017);
        this.buttonsArrayList.add(bt018);
        this.buttonsArrayList.add(bt019);
        this.buttonsArrayList.add(bt020);

        this.buttonsArrayList.add(bt021);
        this.buttonsArrayList.add(bt022);
        this.buttonsArrayList.add(bt023);
        this.buttonsArrayList.add(bt024);
        this.buttonsArrayList.add(bt025);
        this.buttonsArrayList.add(bt026);
        this.buttonsArrayList.add(bt027);
        this.buttonsArrayList.add(bt028);
        this.buttonsArrayList.add(bt029);

        this.buttonsArrayList.add(bt030);
        this.buttonsArrayList.add(bt031);
        this.buttonsArrayList.add(bt032);
        this.buttonsArrayList.add(bt033);
        this.buttonsArrayList.add(bt034);
        this.buttonsArrayList.add(bt035);
        this.buttonsArrayList.add(bt036);
        this.buttonsArrayList.add(bt037);
        this.buttonsArrayList.add(bt038);
        this.buttonsArrayList.add(bt039);

        this.buttonsArrayList.add(bt040);
        this.buttonsArrayList.add(bt041);
        this.buttonsArrayList.add(bt042);
        this.buttonsArrayList.add(bt043);
        this.buttonsArrayList.add(bt044);
        this.buttonsArrayList.add(bt045);
        this.buttonsArrayList.add(bt046);
        this.buttonsArrayList.add(bt047);
        this.buttonsArrayList.add(bt048);
        this.buttonsArrayList.add(bt049);

        this.buttonsArrayList.add(bt050);
        this.buttonsArrayList.add(bt051);
        this.buttonsArrayList.add(bt052);
        this.buttonsArrayList.add(bt053);
        this.buttonsArrayList.add(bt054);
        this.buttonsArrayList.add(bt055);
        this.buttonsArrayList.add(bt056);
        this.buttonsArrayList.add(bt057);
        this.buttonsArrayList.add(bt058);
        this.buttonsArrayList.add(bt059);

        this.buttonsArrayList.add(bt060);
        this.buttonsArrayList.add(bt061);
        this.buttonsArrayList.add(bt062);
        this.buttonsArrayList.add(bt063);
        this.buttonsArrayList.add(bt064);
        this.buttonsArrayList.add(bt065);
        this.buttonsArrayList.add(bt066);
        this.buttonsArrayList.add(bt067);
        this.buttonsArrayList.add(bt068);
        this.buttonsArrayList.add(bt069);

        this.buttonsArrayList.add(bt070);
        this.buttonsArrayList.add(bt071);
        this.buttonsArrayList.add(bt072);
        this.buttonsArrayList.add(bt073);
        this.buttonsArrayList.add(bt074);
        this.buttonsArrayList.add(bt075);
        this.buttonsArrayList.add(bt076);
        this.buttonsArrayList.add(bt077);
        this.buttonsArrayList.add(bt078);
        this.buttonsArrayList.add(bt079);

        this.buttonsArrayList.add(bt080);
        this.buttonsArrayList.add(bt081);
        this.buttonsArrayList.add(bt082);
        this.buttonsArrayList.add(bt083);
        this.buttonsArrayList.add(bt084);
        this.buttonsArrayList.add(bt085);
        this.buttonsArrayList.add(bt086);
        this.buttonsArrayList.add(bt087);
        this.buttonsArrayList.add(bt088);
        this.buttonsArrayList.add(bt089);

        this.buttonsArrayList.add(bt090);
        this.buttonsArrayList.add(bt091);
        this.buttonsArrayList.add(bt092);
        this.buttonsArrayList.add(bt093);
        this.buttonsArrayList.add(bt094);
        this.buttonsArrayList.add(bt095);
        this.buttonsArrayList.add(bt096);
        this.buttonsArrayList.add(bt097);
        this.buttonsArrayList.add(bt098);
        this.buttonsArrayList.add(bt099);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {

            case FILE_CODE:
                if (!checkFilePermission()) {
                    Toast.makeText(this, "You don't have permission to access storage", Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
    }

    private boolean checkFilePermission() {
        return ActivityCompat.checkSelfPermission
                (this, permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission
                (this, permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }


    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }


}
