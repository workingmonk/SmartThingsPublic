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
 */

metadata {
    definition (name: "Iris Button", namespace: "smartthings", author: "SmartThings") {
        capability "Actuator"
        capability "Battery"
        capability "Button"
        capability "Configuration"
        capability "Refresh"

        command "enrollResponse"
        fingerprint inClusters: "0000,0001,0003,0020,0500", outClusters: "0003,0019", manufacturer: "CentraLite", model: "3455-L", deviceJoinName: "Iris button code"

    }

    tiles {

        standardTile("button", "device.button", width: 2, height: 2) {
            state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
            state "button 1 pushed", label: "pushed #1", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#79b821"
        }

        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false) {
            state "battery", label:'${currentValue}% battery', unit:""
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main (["button"])
        details(["button", "battery", "refresh"])
    }
}

def parse(String description) {
    log.debug "description: $description"

    def resultMap = zigbee.getEvent(description)
    if (resultMap) {
        sendEvent(resultMap)
    }
    else {
        def zigbeeMap = zigbee.parseDescriptionAsMap(description)
        log.trace "zigbeeMap : $zigbeeMap"
        if (zigbeeMap?.clusterInt == 0x0001 && zigbeeMap.attrInt == 0x0020) {
            if (description?.startsWith("read attr -")) {
                getBatteryResult(zigbeeMap.value, 3.0, 2.1)
            }
            else if (description?.startsWith("catchall: ")) {
                getBatteryResult(zigbeeMap.data.last(), 3.0, 2.1)
            }
        }
        else if (description?.startsWith('zone status')) {
            parseIasMessage(description)
        }
        else if (description?.startsWith('enroll request')) {
            List cmds = enrollResponse()
            log.debug "enroll response: ${cmds}"
            result = cmds?.collect { new physicalgraph.device.HubAction(it) }
            result
        }
        else {
            log.warn "DID NOT PARSE MESSAGE for description : $description"
        }
    }
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]

    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Released
            //resultMap = getContactResult('closed')
            break

        case '0x0022': // Pressed
            resultMap = getButtonResult('push')
            break
    }
    return resultMap
}

private Map getBatteryResult(rawValue, maxVoltage, minVoltage) {
    def result = [
        name: 'battery'
    ]

    def volts = rawValue / 10
    if (volts > 3.5) {
        result.descriptionText = "${device.displayName} battery has too much power (${volts} volts)."
    }
    else if (volts == 0) {
        result = null
    }
    else {
        if (volts < minVoltage) {
            result.value = 0
        }
        else {
            def pct = (volts - minVoltage) / (maxVoltage - minVoltage)
            result.value = Math.min(100, (int) (pct * 100))
        }

        result.descriptionText = "${device.displayName} battery was ${result.value}%"
    }

    if (result)
        sendEvent(result)
}

def refresh() {
    log.debug "Refreshing Battery"
    zigbee.readAttribute(1, 0x20) + zigbee.enrollResponse()
}

def configure() {
    log.debug "Configuring Reporting, IAS CIE, and Bindings."
    zigbee.batteryConfig() + zigbee.enrollResponse() + zigbee.readAttribute(1, 0x20)
}

def enrollResponse() {
    log.debug "Sending enroll response"
    zigbee.enrollResponse()
}

private Map getButtonResult(value) {
    log.debug 'Button Status'
    value == 'push' ? 'pushed' : 'released'
    sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], descriptionText: "$device.displayName button 1 was pushed", isStateChange: true)
}