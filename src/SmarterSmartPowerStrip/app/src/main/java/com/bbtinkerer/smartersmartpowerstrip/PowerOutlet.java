package com.bbtinkerer.smartersmartpowerstrip;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by bernard on 10/8/2017.
 */

public class PowerOutlet implements Closeable{
    private static final String TAG = PowerOutlet.class.getSimpleName();

    private boolean enabled = true;
    private double current = 0.0;
    private Gpio relayGpio = null;
    private Gpio resetGpio = null;
    private PeripheralManagerService peripheralManagerService = null;

    public PowerOutlet(PeripheralManagerService peripheralManagerService, String relayGpioName, String resetGpioName) throws IOException {
        relayGpio = peripheralManagerService.openGpio(relayGpioName);
        relayGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);

        resetGpio = peripheralManagerService.openGpio(resetGpioName);
        resetGpio.setDirection(Gpio.DIRECTION_IN);
        resetGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
        resetGpio.registerGpioCallback(resetGpioCallback);
    }

    public String getGpioName(){
        return relayGpio.getName();
    }

    public double getCurrent() {
        return current;
    }

    public void setCurrent(double current) {
        this.current = current;
    }

    public void enable() throws IOException {
        setState(true);
    }

    public void disable() throws IOException {
        setState(false);
    }

    public void close() throws IOException{
        relayGpio.close();
        relayGpio = null;
    }

    private void setState(boolean state) throws IOException{
        relayGpio.setValue(state);
        this.enabled = state;
    }

    private GpioCallback resetGpioCallback = new GpioCallback(){
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                Log.d(TAG, "Enabling PowerOutlet " + relayGpio.getName());
                enable();
            }catch (IOException e){
                Log.e(TAG, "Unable to enable: " + e);
            }
            return true;
        }
    };
}