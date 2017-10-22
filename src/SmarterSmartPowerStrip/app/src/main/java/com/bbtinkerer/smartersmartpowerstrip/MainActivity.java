package com.bbtinkerer.smartersmartpowerstrip;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.Closeable;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private final static double MAX_AMPS_TOTAL = 8.0;
    private final static double MAX_AMPS_PER_SOCKET = 5.0;
    private final static long DELAY_BETWEEN_SCANS = 35;
    private final static String[] OULTET_RELAY_GPIO_NAMES = {"GPIO1_IO18", "GPIO4_IO19"};
    private final static String[] OULTET_RESET_GPIO_NAMES = {"GPIO4_IO21", "GPIO4_IO22"};
    private final static String I2C_NAME = "I2C2";
    private final static int[] I2C_CURRENT_SENSOR_ADDRESSES = {0x08};//, 0x09};

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final PeripheralManagerService peripheralManagerService = new PeripheralManagerService();
    private I2cDevice[] i2cDevices = new I2cDevice[I2C_CURRENT_SENSOR_ADDRESSES.length];
    private PowerOutlet[] outlets = new PowerOutlet[OULTET_RELAY_GPIO_NAMES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            for(int i = 0; i < OULTET_RELAY_GPIO_NAMES.length; i++){
                outlets[i] = new PowerOutlet(peripheralManagerService, OULTET_RELAY_GPIO_NAMES[i], OULTET_RESET_GPIO_NAMES[i]);
            }

            for(int i = 0; i < I2C_CURRENT_SENSOR_ADDRESSES.length; i++){
                i2cDevices[i] = peripheralManagerService.openI2cDevice(I2C_NAME, I2C_CURRENT_SENSOR_ADDRESSES[i]);
            }

        }catch(IOException e){
            Log.e(TAG, "Error initializing: " + e);
        }

        final Callable<Void> scan = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                while (true) {
                    TimeUnit.MILLISECONDS.sleep(DELAY_BETWEEN_SCANS);
                    performScan();
                    checkIndividualCurrent();
                    checkTotalCurrent();
                }
            }
        };
        executorService.submit(scan);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO clean up gpios and i2creader
        for(int i = 0; i < OULTET_RELAY_GPIO_NAMES.length; i++){
            if(outlets[i] != null){
                try{
                    outlets[i].close();
                    outlets[i] = null;
                }catch (IOException e){
                    Log.w(TAG, "Unable to close outlet: " + e);
                }
            }
        }
        for(int i = 0; i < I2C_CURRENT_SENSOR_ADDRESSES.length; i++){
            if(i2cDevices[i] != null){
                try{
                    i2cDevices[i].close();
                    i2cDevices[i] = null;
                }catch (IOException e){
                    Log.w(TAG, "Unable to close i2cDevice: " + e);
                }
            }
        }

        executorService.shutdownNow();
    }

    private void closePio(Closeable closeable){
        try{
            closeable.close();
        }catch (IOException e){
            Log.w(TAG, "Unable to close: " + e);
        }
    }

    private void performScan() {
        try {
            Log.d(TAG, "performing scan");
            int outletIndex = 0;
            for(int i = 0; i < I2C_CURRENT_SENSOR_ADDRESSES.length; i++){
                byte bytes[] = new byte[]{0, 0, 0, 0};
                i2cDevices[i].readRegBuffer(0, bytes, 4);
                outlets[outletIndex++].setCurrent(convertSensorData(bytes[0], bytes[1]));
                outlets[outletIndex++].setCurrent(convertSensorData(bytes[2], bytes[3]));
            }

            Log.d(TAG, getDebugCurrentString());
        } catch (final IOException e) {
            Log.e(TAG, "Error: " + e);
        } catch (final Exception e){
            Log.e(TAG, "Exception: " + e);
        }
    }

    private String getDebugCurrentString(){
        String result = "";
        for(int i = 0; i < OULTET_RELAY_GPIO_NAMES.length; i++){
            result += outlets[i].getGpioName() + ": " + outlets[i].getCurrent() + "   ";
        }
        return result;
    }

    private double convertSensorData(byte high, byte low){
        double result = 0;
        int value = 0;
        value = ((high & 0xFF) << 8) | (low & 0xFF);
        result = value / 1000.0;
        Log.v(TAG, String.format(Locale.US, "conversionValue: %f", result));
        return result;
    }

    private void checkIndividualCurrent(){
        for(int i = 0; i < OULTET_RELAY_GPIO_NAMES.length; i++){
            if(outlets[i].getCurrent() > MAX_AMPS_PER_SOCKET){
                try {
                    Log.d(TAG, "Over individual current, turning off " + outlets[i].getGpioName());
                    outlets[i].disable();
                }catch(IOException e){
                    Log.e(TAG, "Error, unable to disable " + outlets[i].getGpioName() + ": " + e);
                }
            }
        }
    }

    private void checkTotalCurrent(){
        double total = getTotalCurrent();
        int index = OULTET_RELAY_GPIO_NAMES.length - 1;
        while(total > MAX_AMPS_TOTAL){
            // check if relay1 active, if so, turn off
            Log.d(TAG, "Exceeding total current");
            try {
                Log.v(TAG, "Over individual current, turning off " + outlets[index].getGpioName());
                outlets[index].disable();
                total -= outlets[index].getCurrent();
                index--;
            }catch(IOException e){
                Log.e(TAG, "Error, unable to disable outlet2: " + e);
            }
        }
    }

    private double getTotalCurrent(){
        double total = 0;
        for(int i = 0; i < OULTET_RELAY_GPIO_NAMES.length; i++){
            total += outlets[i].getCurrent();
        }
        return total;
    }
}

