package at.maui.cardar.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardDeviceParams;
import com.google.vrtoolkit.cardboard.CardboardView;

import at.maui.cardar.R;
import at.maui.cardar.ui.ar.Renderer;
import at.maui.cardar.ui.widget.CardboardOverlayView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import timber.log.Timber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

public class ArActivity extends CardboardActivity {

    @InjectView(R.id.overlay)
    CardboardOverlayView overlayView;

    @InjectView(R.id.cardboard_view)
    CardboardView cardboardView;

    private Renderer mRenderer;

    private Vibrator mVibrator;
    private int currHUD = 0;

    //
    //----------- BLUETOOTH STUFF ------------//
    //
    private static final String TAG = "bluetooth2";

    String txtArduino;
    static Handler h;

    final int RECIEVE_MESSAGE = 1;        // Status  for Handler
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module (you must edit this line)
    private static String address = "20:15:05:14:09:37";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ar);

        // Inject views
        ButterKnife.inject(this);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Associate a CardboardView.StereoRenderer with cardboardView.
        mRenderer = new Renderer(this);
        cardboardView.setRenderer(mRenderer);

        // Associate the cardboardView with this activity.
        setCardboardView(cardboardView);


        // for display the received data from the Arduino
        txtArduino = "";

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:                                                   // if receive massage
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);                 // create string from bytes array
                        sb.append(strIncom);                                                // append string
                        int endOfLineIndex = sb.indexOf("\r\n");                            // determine the end-of-line
                        if (endOfLineIndex > 0) {                                            // if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);               // extract string
                            sb.delete(0, sb.length());                                      // and clear
                            txtArduino = sbprint;            // update TextView

                            //Steps, pulse, muscle, fire, direction
                            Calendar c = Calendar.getInstance();
                            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                            String formattedDate = df.format(c.getTime());
                            String[] txtSplit = txtArduino.split("\\s+");

                            if(txtSplit.length == 5) {
                                overlayView.show3DHUDItems(formattedDate + "\n" +
                                        getString(R.string.hud_pulse) + " " + txtSplit[1] + "\n" +
                                        getString(R.string.hud_muscle) + " " + txtSplit[2] + "\n" +
                                        getString(R.string.hud_direction) + " " + txtSplit[4] + "\n");

                                if (Integer.parseInt(txtSplit[3]) == 4) {
                                    overlayView.show3DExplosion(currHUD);
                                }
                            }
                        }

                        //Log.d(TAG, "...String:"+ sb.toString() +  "Byte:" + msg.arg1 + "...");
                        break;
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();

        //mConnectedThread.write("1");

    }

    @Override
    public void onCardboardTrigger() {
        Timber.i("onCardboardTrigger");

//        if (mRenderer.isLookingAtObject()) {
//            mScore++;
//            overlayView.show3DToast("Found it! Look around for another one.\nScore = " + mScore);
//            mRenderer.hideObject();
//        } else {
//            overlayView.show3DToast("Look around to find the object!");
//        }
//        // Always give user feedback

        if(currHUD == 0) {
            overlayView.show3DToast("PRIMARY" + "\n" + "-------------------\n|   " +
                    getString(R.string.weap_ionbeam) + " |\n" +
                    "-------------------\n" +
                    getString(R.string.weap_rocketlauncher) + "\n" +
                    getString(R.string.weap_watergun));
            overlayView.changeGun(currHUD);
            currHUD = 1;
        }
        else if(currHUD == 1) {
            overlayView.show3DToast("PRIMARY" + "\n" +"-----------------------\n| " +
                                    getString(R.string.weap_rocketlauncher) + " |\n" +
                                    "-----------------------\n" +
                                    getString(R.string.weap_watergun) + "\n" +
                                    getString(R.string.weap_ionbeam));
            overlayView.changeGun(currHUD);
            currHUD = 2;
        }
        else if(currHUD == 2) {
            overlayView.show3DToast("PRIMARY" + "\n" +"-------------------\n| " +
                                    getString(R.string.weap_watergun) + " |\n" +
                                    "-------------------\n" +
                                    getString(R.string.weap_ionbeam) + "\n" +
                                    getString(R.string.weap_rocketlauncher));
            overlayView.changeGun(currHUD);
            currHUD = 0;
        }
        mVibrator.vibrate(50);
    }


//
//------- BLUETOOTH FUNCTIONS------//
//

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "....Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            errorExit("Fatal Error", "Bluetooth not support");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }
    }
}