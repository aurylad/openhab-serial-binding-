/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.uvsensor.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.TooManyListenersException;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.io.transport.serial.PortInUseException;
import org.eclipse.smarthome.io.transport.serial.SerialPort;
import org.eclipse.smarthome.io.transport.serial.SerialPortEvent;
import org.eclipse.smarthome.io.transport.serial.SerialPortEventListener;
import org.eclipse.smarthome.io.transport.serial.SerialPortIdentifier;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.eclipse.smarthome.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UvSensorHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dovydas - Initial contribution
 */
public class UvSensorHandler extends BaseThingHandler implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(UvSensorHandler.class);

    private final SerialPortManager serialPortManager;

    private @Nullable UvSensorConfiguration config;
    private SerialPortIdentifier portId;
    private SerialPort serialPort;

    private InputStream inputStream;

    // For testing case;
    // private BufferedReader input;
    // End

    public UvSensorHandler(Thing thing, final SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public void initialize() {

        config = getConfigAs(UvSensorConfiguration.class);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.

        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");

        String port = (String) getConfig().get(UvSensorBindingConstants.PARAMETER_CONFIG);
        if (port == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port must be set!");
            return;
        }

        portId = serialPortManager.getIdentifier(port);
        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port is not known!");
            return;
        }

        try {
            serialPort = portId.open(getThing().getUID().toString(), 2000);
            System.out.println("Serial port info: " + serialPort);
            serialPort.addEventListener(this);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN);
            serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

            // activate the DATA_AVAILABLE notifier
            serialPort.notifyOnDataAvailable(true);
            inputStream = serialPort.getInputStream();
            System.out.println("Input stream: " + inputStream);

            // For testing case
            InputStream inputStream = serialPort.getInputStream();
            InputStreamReader inputStreamReqader = new InputStreamReader(inputStream);
            // input = new BufferedReader(inputStreamReqader);
            // end

            updateStatus(ThingStatus.ONLINE);
        } catch (final IOException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "I/O error!");
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "Port is in use!");
        } catch (TooManyListenersException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Cannot attach listener to port!");
        } catch (UnsupportedCommOperationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Unsupported communication operation");
        }

    }

    @Override
    public void serialEvent(SerialPortEvent event) {

        if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {

            try {
                byte[] buffer = this.readBuffer();

                if (isHeaderCorrect(buffer)) {

                    double pm25 = calculatePolution(buffer[3], buffer[2]);
                    double pm10 = calculatePolution(buffer[5], buffer[4]);

                    // updateState(UvSensorBindingConstants.PARTICLE_CHANNEL_PM25, new DecimalType(pm25));
                    // updateState(UvSensorBindingConstants.PARTICLE_CHANNEL_PM10, new DecimalType(pm10));

                    System.out.println(pm25 + " μg/m^3 - Fine particles leve          |         " + pm10
                            + " μg/m^3 - Coarse dust level");
                }
            } catch (Exception e1) {
                logger.debug("Error reading from serial port: {}", e1.getMessage(), e1);
            }
        }
    }

    private byte[] readBuffer() {
        byte[] buffer = new byte[UvSensorBindingConstants.SERIAL_BUFFER_SIZE];
        try {
            inputStream.read(buffer, 0, UvSensorBindingConstants.SERIAL_BUFFER_SIZE);
        } catch (IOException e) {
            logger.error("Error reading from serial port: {}", e.getMessage(), e);
        }
        return buffer;
    }

    private boolean isHeaderCorrect(byte[] buffer) {
        return DatatypeConverter.printHexBinary(Arrays.copyOfRange(buffer, 0, 1)).equals("AA")
                && DatatypeConverter.printHexBinary(Arrays.copyOfRange(buffer, 1, 2)).equals("C0");

    }

    private double calculatePolution(byte highByte, byte lowByte) {

        return ((unsignByte(highByte)) * 256 + (unsignByte(lowByte))) / 10.0;
    }

    private int unsignByte(byte value) {
        return value & 0xFF;
    }

}
