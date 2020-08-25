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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link UvSensorBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dovydas - Initial contribution
 */
@NonNullByDefault
public class UvSensorBindingConstants {

    private static final String BINDING_ID = "uvsensor";

    // // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE = new ThingTypeUID(BINDING_ID, "uvsensor");

    public static final String PARTICLE_CHANNEL_PM25 = "pm25";

    public static final String PARTICLE_CHANNEL_PM10 = "pm10";

    public static final String PARAMETER_CONFIG = "port";

    public static final int SERIAL_BUFFER_SIZE = 16;

}
