/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ZigBee RGBW Bulb
 *
 *  Author: SmartThings
 *  Date: 2015-12-09
 */

metadata {
    definition (name: "ZigBee RGBW Bulb", namespace: "smartthings", author: "SmartThings") {

        capability "Actuator"
        capability "Color Control"
        capability "Color Temperature"
        capability "Configuration"
        capability "Polling"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"

        attribute "colorName", "string"
        command "setGenericName"
        command "setAdjustedColor"

        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0300,0B04,FC0F", outClusters: "0019", manufacturer: "OSRAM", model: "LIGHTIFY Flex RGBW", deviceJoinName: "OSRAM LIGHTIFY LED FLEXIBLE STRIP RGBW"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0300,0B04,FC0F", outClusters: "0019", manufacturer: "OSRAM", model: "Flex RGBW", deviceJoinName: "OSRAM LIGHTIFY LED FLEXIBLE STRIP RGBW"
    }

    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
            tileAttribute ("device.color", key: "COLOR_CONTROL") {
                attributeState "color", action:"color control.setColor"
            }
        }
        controlTile("colorTempSliderControl", "device.colorTemperature", "slider", height: 1, width: 2, inactiveLabel: false, range:"(2700..6500)") {
            state "colorTemperature", action:"color temperature.setColorTemperature"
        }
        valueTile("colorTemp", "device.colorTemperature", inactiveLabel: false, decoration: "flat") {
            state "colorTemperature", label: '${currentValue} K'
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "colorTempSliderControl", "colorTemp", "refresh"])
    }
}

//Globals
private getATTRIBUTE_HUE() { 0x0000 }
private getATTRIBUTE_SATURATION() { 0x0001 }
private getHUE_COMMAND() { 0x00 }
private getSATURATION_COMMAND() { 0x03 }

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"

    def finalResult = zigbee.getEvent(description)
    if (finalResult) {
        log.info finalResult
        sendEvent(finalResult)
    }
    else {
        def zigbeeMap = zigbee.parseDescriptionAsMap(description)
        log.trace "zigbeeMap : $zigbeeMap"

        if (zigbeeMap?.clusterInt == zigbee.CLUSTER_COLOR_CONTROL) {
            if(zigbeeMap.attrInt == ATTRIBUTE_HUE){  //Hue Attribute
                def hueValue = Math.round(convertHexToInt(zigbeeMap.value) / 255 * 360)
                log.debug "Hue value returned is $hueValue"
                sendEvent(name: "hue", value: hueValue, displayed:false)
            }
            else if(zigbeeMap.attrInt == ATTRIBUTE_SATURATION){ //Saturation Attribute
                def saturationValue = Math.round(convertHexToInt(zigbeeMap.value) / 255 * 100)
                log.debug "Saturation from refresh is $saturationValue"
                sendEvent(name: "saturation", value: saturationValue, displayed:false)
            }
        }
        else {
            log.warn "DID NOT PARSE MESSAGE for description : $description"
        }
    }
}

def on() {
    zigbee.on()
}

def off() {
    zigbee.off()
}

def refresh() {
    zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x00) + zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, 0x00)
        + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, 0x00) + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, zigbee.ATTRIBUTE_COLOR_TEMPERATURE)
        + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, ATTRIBUTE_HUE) + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, ATTRIBUTE_SATURATION)
        + zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.colorTemperatureConfig()
        + zigbee.configureReporting(COLOR_CONTROL_CLUSTER, ATTRIBUTE_HUE, 0x20, 1, 3600, 0x01)
        + zigbee.configureReporting(COLOR_CONTROL_CLUSTER, ATTRIBUTE_SATURATION, 0x20, 1, 3600, 0x01)
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.colorTemperatureConfig()
        + zigbee.configureReporting(COLOR_CONTROL_CLUSTER, ATTRIBUTE_HUE, 0x20, 1, 3600, 0x01)
        + zigbee.configureReporting(COLOR_CONTROL_CLUSTER, ATTRIBUTE_SATURATION, 0x20, 1, 3600, 0x01)
        + zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x00) + zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, 0x00)
        + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, 0x00) + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, zigbee.ATTRIBUTE_COLOR_TEMPERATURE)
        + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, ATTRIBUTE_HUE) + zigbee.readAttribute(zigbee.COLOR_CONTROL_CLUSTER, ATTRIBUTE_SATURATION)
}

def setColorTemperature(value) {
    zigbee.setColorTemperature(value)
}

def setLevel(value) {
    zigbee.setLevel(value)
}

def setColor(value){
    log.trace "setColor($value)"

    if (value.hex) { sendEvent(name: "color", value: value, displayed:false)}
    sendEvent(name: "colorTemperature", value: "--", displayed:false)

    //sendEvent(name: "hue", value: value.hue, displayed:false)
    //sendEvent(name: "saturation", value: value.saturation, displayed:false)
    def scaledHueValue = zigbee.convertToHexString(Math.round(value.hue * 0xfe / 100.0), 2)
    def scaledSatValue = zigbee.convertToHexString(Math.round(value.saturation * 0xfe / 100.0), 2)

    def cmd = []
    if (device.latestValue("switch") == "off") {
        cmd += zigbee.on()
    }

    cmd += zigbee.command(zigbee.COLOR_CONTROL_CLUSTER, HUE_COMMAND, ${scaledHueValue}, "00", "0500")       //payload-> hue value, direction (00-> shortest distance), transition time (1/10th second) (0500 in U16 reads 5)
    cmd += zigbee.command(zigbee.COLOR_CONTROL_CLUSTER, SATURATION_COMMAND, ${scaledSatValue}, "0500")      //payload-> sat value, transition time

    cmd
}

def setHue(value) {
    def scaledHueValue = zigbee.convertToHexString(Math.round(value * 0xfe / 100.0), 2)
    zigbee.command(zigbee.COLOR_CONTROL_CLUSTER, HUE_COMMAND, ${scaledHueValue}, "00", "0500")       //payload-> hue value, direction (00-> shortest distance), transition time (1/10th second) (0500 in U16 reads 5)
}

def setSaturation(value) {
    def scaledSatValue = zigbee.convertToHexString(Math.round(value.saturation * 0xfe / 100.0), 2)
    zigbee.command(zigbee.COLOR_CONTROL_CLUSTER, SATURATION_COMMAND, ${scaledSatValue}, "0500")      //payload-> sat value, transition time
}