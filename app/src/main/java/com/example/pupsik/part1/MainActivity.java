package com.example.pupsik.part1;

import android.app.Activity;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import android.os.Handler;


public class MainActivity extends Activity {
    private BluetoothAdapter bluetooth;
    private BluetoothSocket socket;
    private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //uuid абсолютно рандомный
    private static int DISCOVERY_REQUEST = 1;
    private ArrayList<BluetoothDevice> foundDevices;

    private ArrayList<String> mDeviceList = new ArrayList<String>();

    private ArrayAdapter<BluetoothDevice> aa;
    private ListView list;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDeviceList.add(device.getName() + "\n" + device.getAddress());
                Log.i("BT", device.getName() + "\n" + device.getAddress());
                list.setAdapter(new ArrayAdapter<String>(context,
                        android.R.layout.simple_list_item_1, mDeviceList));
            }
        }
    };

    BroadcastReceiver discoveryResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(bluetooth.getBondedDevices().contains(remoteDevice)){
                foundDevices.add(remoteDevice);
                aa.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configureBluetooth();

        setupListView();

       // setupSearchButton();

        setupListenButton();

    }

    private void configureBluetooth(){
        bluetooth = BluetoothAdapter.getDefaultAdapter();
    }
      private void setupListView(){
       // aa = new ArrayAdapter<BluetoothDevice>(this,android.R.layout.simple_list_item_1,foundDevices);
        list = (ListView)findViewById(R.id.blutList);
        //list.setAdapter(aa);
          bluetooth.startDiscovery();

          IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
          registerReceiver(mReceiver, filter);


          //Крашится вот тут!!!!
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AsyncTask<Integer, Void, Void> connectTask = new AsyncTask<Integer, Void, Void>() {
                    @Override
                      protected Void doInBackground(Integer... params) {
                        try{
                            BluetoothDevice device = foundDevices.get(params[0]);
                           // socket = device.createRfcommSocketToServiceRecord(uuid);
                            Method m = device.getClass().getMethod("createRfcommSocket",new Class[] { int.class });
                            socket = (BluetoothSocket)m.invoke(device, Integer.valueOf(1));
                            //две строчки сверху - костыль
                            socket.connect();
                        }catch (IOException e){
                            Log.e("BLUETOOTH_CLIENT", e.getMessage());
                        }catch (NoSuchMethodException e){
                            Log.e("NoSuchMethodException", e.getMessage());
                        }catch (IllegalAccessException e){
                            Log.e("IllegalAccessException", e.getMessage());
                        }catch (InvocationTargetException e){
                            Log.e("InvocationTarget", e.getMessage());
                        }
                        return null;
                    }
                    @Override
                    protected void onPostExecute(Void result){
                      //  switchUI();
                    }
                };
                connectTask.execute(position);
            }
        });
    }


    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }



//Тут тоже крашится, но эта функция по сути не нужна

    private void setupSearchButton(){
        Button searchButton = (Button)findViewById(R.id.button2);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerReceiver(discoveryResult, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                if(!bluetooth.isDiscovering()){
                    foundDevices.clear();
                    bluetooth.startDiscovery();
                }
            }
        });
    }

    private void setupListenButton(){
        Button listenButton = (Button)findViewById(R.id.button);
        listenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent disc = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(disc, DISCOVERY_REQUEST);
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == DISCOVERY_REQUEST){
            boolean isDiscovered = resultCode > 0;
            if(isDiscovered){
                String name = "bluetoothserver";
                try{
                    final BluetoothServerSocket btserver = bluetooth.listenUsingRfcommWithServiceRecord(name,uuid);

                    AsyncTask<Integer, Void, BluetoothSocket> acceptThread = new AsyncTask<Integer, Void, BluetoothSocket>() {
                        @Override
                        protected BluetoothSocket doInBackground(Integer... params) {
                            try{
                                socket = btserver.accept(params[0] * 1000);
                                return socket;
                            }catch (IOException e){
                                Log.d("BLUETOOTH", e.getMessage());
                            }
                            return null;
                        }
                        @Override
                        protected void onPostExecute(BluetoothSocket result){
                            if(result != null){
                                switchUI();
                            }
                        }
                    };
                    acceptThread.execute(resultCode);
                } catch (IOException e){
                    Log.d("BLUETOOTH", e.getMessage());
                }
            }
        }
    }

    private Handler handler = new Handler();
    private void switchUI(){
        final TextView messageText = (TextView)findViewById(R.id.textView);
        final EditText textEntry = (EditText)findViewById(R.id.editText);
        list.setVisibility(View.GONE);
        messageText.setVisibility(View.VISIBLE);
        textEntry.setEnabled(true);

        textEntry.setOnKeyListener(new View.OnKeyListener() {
            @Override //сомнительно
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)){
                    sendMessage(socket, textEntry.getText().toString());
                    textEntry.setText("");
                    return true;
                }
                return false;
            }
        });
        BluetoothSocketListener bs1 = new BluetoothSocketListener(socket, handler, messageText);
        Thread messageListener = new Thread(bs1);
        messageListener.start();
    }

    private void sendMessage(BluetoothSocket socket, String msg){
        OutputStream outStream;
        try{
            outStream = socket.getOutputStream();
            byte[] byteString = (msg + " ").getBytes();
            //stringAsBytes[byteString.length-1] = 0;
            outStream.write(byteString);
        }catch (IOException e){
            Log.d("BLUETOOTH_COMMS", e.getMessage());
        }
    }

    private class MessagePoster implements Runnable{
        private TextView textView;
        private String message;

        public MessagePoster(TextView textView, String message){
            this.textView = textView;
            this.message = message;
        }
        public void run(){
            textView.setText(message);
        }
    }
    private class BluetoothSocketListener implements Runnable{
        private BluetoothSocket socket;
        private TextView textView;
        private Handler handler;

        public BluetoothSocketListener(BluetoothSocket socket, Handler handler, TextView textView){
            this.socket = socket;
            this.handler = handler;
            this.textView = textView;
        }

        public void run(){
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            try{
                InputStream instream = socket.getInputStream();
                int bytesRead = -1;
                String message = "";
                while (true) {
                    message = "";
                    bytesRead = instream.read(buffer);
                    if (bytesRead != -1) {
                        while ((bytesRead == bufferSize) && (buffer[bufferSize - 1] != 0)) {
                            message = message + new String(buffer, 0, bytesRead);
                            bytesRead = instream.read(buffer);
                        }

                    }
                    message = message + new String(buffer, 0, bytesRead - 1);
                    handler.post(new MessagePoster(textView, message));
                    socket.getInputStream();
                }
            }catch (IOException e){
                Log.d("BLUETOOTH_COMMS", e.getMessage());
            }
        }
    }
   }