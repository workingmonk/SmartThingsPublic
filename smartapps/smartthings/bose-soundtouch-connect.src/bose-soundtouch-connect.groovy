/**
 *  Bose SoundTouch (Connect)
 *
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
 definition(
    name: "Bose SoundTouch (Connect)",
    namespace: "smartthings",
    author: "SmartThings",
    description: "Control your Bose SoundTouch speakers",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true
)

preferences {
    page(name:"deviceDiscovery", title:"Device Setup", content:"deviceDiscovery", refreshTimeout:5)
}

/**
 * Get the urn that we're looking for
 *
 * @return URN which we are looking for
 *
 * @todo This + getUSNQualifier should be one and should use regular expressions
 */
def getDeviceType() {
    return "urn:schemas-upnp-org:device:MediaRenderer:1" // Bose
}

/**
 * If not null, returns an additional qualifier for ssdUSN
 * to avoid spamming the network
 *
 * @return Additional qualifier OR null if not needed
 */
def getUSNQualifier() {
    return "uuid:BO5EBO5E-F00D-F00D-FEED-"
}

/**
 * Get the name of the new device to instantiate in the user's smartapps
 * This must be an app owned by the namespace (see #getNameSpace).
 *
 * @return name
 */
def getDeviceName() {
    return "Bose SoundTouch"
}

/**
 * Returns the namespace this app and siblings use
 *
 * @return namespace
 */
def getNameSpace() {
    return "smartthings"
}

/**
 *
 * @return harcoded communication port 8090
 */
private String getBosePort() { "8090" }

/**
 * The deviceDiscovery page used by preferences. Will automatically
 * make calls to the underlying discovery mechanisms as well as update
 * whenever new devices are discovered AND verified.
 *
 * @return a dynamicPage() object
 */
def deviceDiscovery() {
    def refreshInterval = 3 // Number of seconds between refresh
    int deviceRefreshCount = !state.deviceRefreshCount ? 0 : state.deviceRefreshCount as int
    state.deviceRefreshCount = deviceRefreshCount + refreshInterval

    def devices = getSelectableDevice()
    def numFound = devices.size() ?: 0

    ssdpSubscribe()

    //device discovery request every 15s
    if((deviceRefreshCount % 15) == 0) {
        discoverDevices()
    }

    // Verify request every 3 seconds except on discoveries
    if(((deviceRefreshCount % 3) == 0) && ((deviceRefreshCount % 15) != 0)) {
        verifyDevices()
    }

    log.trace "Discovered devices: ${devices}"

    return dynamicPage(name:"deviceDiscovery", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
        section("Please wait while we discover your ${getDeviceName()}. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
            input "selecteddevice", "enum", required:false, title:"Select ${getDeviceName()} (${numFound} found)", multiple:true, options:devices
        }
    }
}

void ssdpSubscribe() {
    subscribe(location, "ssdpTerm.${deviceType}", ssdpHandler)
}

def ssdpHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
    def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub":hub]
    //log.debug parsedEvent

    if ((parsedEvent?.ssdpTerm?.contains(getDeviceType())) && (parsedEvent?.ssdpUSN?.contains(getUSNQualifier()))) {
        def USN = parsedEvent.ssdpUSN.toString()
        def devices = getDevices()
        if (!(devices."${USN}")) {
            //device does not exist
            log.trace "parseSDDP() Adding Device \"${USN}\" to known list"
            devices << ["${USN}":parsedEvent]
        } else {
            // update the values
            def d = devices."${USN}"

            if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
                log.trace "parseSSDP() Updating device location (ip & port)"
                d.networkAddress = parsedEvent.networkAddress
                d.deviceAddress = parsedEvent.deviceAddress
                child = getChildDevice(parsedEvent.mac)
                if (child) {
                    child.sync(parsedEvent.networkAddress, bosePort)
                }
            }
        }
    }
}

/**
 * Called by SmartThings Cloud when user has selected device(s) and
 * pressed "Install".
 */
def installed() {
    log.trace "Installed with settings: ${settings}"
    initialize()
}

/**
 * Called by SmartThings Cloud when app has been updated
 */
def updated() {
    log.trace "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

/**
 * Called by SmartThings Cloud when user uninstalls the app
 *
 * We don't need to manually do anything here because any children
 * are automatically removed upon the removal of the parent.
 *
 * Only time to do anything here is when you need to notify
 * the remote end. And even then you're discouraged from removing
 * the children manually.
 */
def uninstalled() {
}

/**
 * If user has selected devices, will start monitoring devices
 * for changes (new address, port, etc...)
 */
def initialize() {
    log.trace "initialize()"
    unsubscribe()
    unschedule()

    if (selecteddevice) {
        ssdpSubscribe()
        addDevice()
        refreshDevices()
    }
}

/**
 * Adds the child devices based on the user's selection
 *
 * Uses selecteddevice defined in the deviceDiscovery() page
 */
def addDevice(){
    def devices = getVerifiedDevices()
    def devlist
    log.trace "Adding childs"

    // If only one device is selected, we don't get a list (when using simulator)
    if (!(selecteddevice instanceof List)) {
        devlist = [selecteddevice]
    } else {
        devlist = selecteddevice
    }

    log.trace "These are being installed: ${devlist}"

    devlist.each { dni ->
        def d = getChildDevice(dni)
        if(!d) {
            def newDevice = devices.find { (it.value.mac) == dni }
            def deviceName = newDevice?.value.name
            if (!deviceName)
                deviceName = getDeviceName() + "[${newDevice?.value.name}]"
            d = addChildDevice(getNameSpace(), getDeviceName(), dni, newDevice?.value.hub, [
                    "label": deviceName,
                    "data": [
                            "mac": newDevice.value.mac,
                            "ip": newDevice.value.networkAddress,
                            "port": bosePort
                    ]
            ])
            d.boseSetDeviceID(newDevice.value.deviceID)
            log.trace "Created ${d.displayName} with id $dni"
        } else {
            log.trace "${d.displayName} with id $dni already exists"
        }
    }
}

/**
 * Resolves a DeviceNetworkId to an address. Primarily used by children
 *
 * @param dni Device Network id
 * @return address or null
 */
//@Deprecated
def resolveDNI2Address(dni) {
    def device = getVerifiedDevices().find { (it.value.mac) == dni }
    if (device) {
        //first setup the sync so that this method isn't called again
        def child = getChildDevice(dni)
        if (child) {
            child.sync(device.value.networkAddress, bosePort)      //bose connect gets port as 8091, but uses 8090 for communication
        }
        return convertHexToIP(device.value.networkAddress)
    }
    return null
}

/**
 * Joins a child to the "Play Everywhere" zone
 *
 * @param child The speaker joining the zone
 * @return A list of maps with POST data
 */
def boseZoneJoin(child) {
    log = child.log // So we can debug this function

    def results = []
    def result = [:]

    // Find the master (if any)
    def server = getChildDevices().find{ it.boseGetZone() == "server" }

    if (server) {
        log.debug "boseJoinZone() We have a server already, so lets add the new speaker"
        child.boseSetZone("client")

        result['endpoint'] = "/setZone"
        result['host'] = server.getDeviceIP() + ":" + bosePort
        result['body'] = "<zone master=\"${server.boseGetDeviceID()}\" senderIPAddress=\"${server.getDeviceIP()}\">"
        getChildDevices().each{ it ->
            log.trace "child: " + child
            log.trace "zone : " + it.boseGetZone()
            if (it.boseGetZone() || it.boseGetDeviceID() == child.boseGetDeviceID())
                result['body'] = result['body'] + "<member ipaddress=\"${it.getDeviceIP()}\">${it.boseGetDeviceID()}</member>"
        }
        result['body'] = result['body'] + '</zone>'
    } else {
        log.debug "boseJoinZone() No server, add it!"
        result['endpoint'] = "/setZone"
        result['host'] = child.getDeviceIP() + ":" + bosePort
        result['body'] = "<zone master=\"${child.boseGetDeviceID()}\" senderIPAddress=\"${child.getDeviceIP()}\">"
        result['body'] = result['body'] + "<member ipaddress=\"${child.getDeviceIP()}\">${child.boseGetDeviceID()}</member>"
        result['body'] = result['body'] + '</zone>'
        child.boseSetZone("server")
    }
    results << result
    return results
}

def boseZoneReset() {
    getChildDevices().each{ it.boseSetZone(null) }
}

def boseZoneHasMaster() {
    return getChildDevices().find{ it.boseGetZone() == "server" } != null
}

/**
 * Removes a speaker from the play everywhere zone.
 *
 * @param child Which speaker is leaving
 * @return a list of maps with POST data
 */
def boseZoneLeave(child) {
    log = child.log // So we can debug this function

    def results = []
    def result = [:]

    // First, tag us as a non-member
    child.boseSetZone(null)

    // Find the master (if any)
    def server = getChildDevices().find{ it.boseGetZone() == "server" }

    if (server && server.boseGetDeviceID() != child.boseGetDeviceID()) {
        log.debug "boseLeaveZone() We have a server, so tell him we're leaving"
        result['endpoint'] = "/removeZoneSlave"
        result['host'] = server.getDeviceIP() + ":" + bosePort
        result['body'] = "<zone master=\"${server.boseGetDeviceID()}\" senderIPAddress=\"${server.getDeviceIP()}\">"
        result['body'] = result['body'] + "<member ipaddress=\"${child.getDeviceIP()}\">${child.boseGetDeviceID()}</member>"
        result['body'] = result['body'] + '</zone>'
        results << result
    } else {
        log.debug "boseLeaveZone() No server, then...uhm, we probably were it!"
        // Dismantle the entire thing, first send this to master
        result['endpoint'] = "/removeZoneSlave"
        result['host'] = child.getDeviceIP() + ":" + bosePort
        result['body'] = "<zone master=\"${child.boseGetDeviceID()}\" senderIPAddress=\"${child.getDeviceIP()}\">"
        getChildDevices().each{ dev ->
            if (dev.boseGetZone() || dev.boseGetDeviceID() == child.boseGetDeviceID())
                result['body'] = result['body'] + "<member ipaddress=\"${dev.getDeviceIP()}\">${dev.boseGetDeviceID()}</member>"
        }
        result['body'] = result['body'] + '</zone>'
        results << result

        // Also issue this to each individual client
        getChildDevices().each{ dev ->
            if (dev.boseGetZone() && dev.boseGetDeviceID() != child.boseGetDeviceID()) {
                log.trace "Additional device: " + dev
                result['host'] = dev.getDeviceIP() + ":" + bosePort
                results << result
            }
        }
    }

    return results
}

/**
 * Generates a Map object which can be used with a preference page
 * to represent a list of devices detected and verified.
 *
 * @return Map with zero or more devices
 */
Map getSelectableDevice() {
    def devices = getVerifiedDevices()
    def map = [:]
    devices.each {
        def value = "${it.value.name}"
        def key = it.value.mac
        map["${key}"] = value
    }
    map
}

/**
 * Starts the refresh loop, making sure to keep us up-to-date with changes
 *
 */
private refreshDevices() {
    discoverDevices()
    verifyDevices()
    runIn(300, "refreshDevices")
}

/**
 * Issues a SSDP M-SEARCH over the LAN for a specific type (see getDeviceType())
 */
private discoverDevices() {
    log.trace "discoverDevice() Issuing SSDP request"
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery ${getDeviceType()}", physicalgraph.device.Protocol.LAN))
}

/**
 * Walks through the list of unverified devices and issues a verification
 * request for each of them (basically calling verifyDevice() per unverified)
 */
private verifyDevices() {
    def devices = getDevices().findAll { it?.value?.verified != true }

    devices.each {
        verifyDevice(
            it?.value?.mac,
            convertHexToIP(it?.value?.networkAddress),
            convertHexToInt(it?.value?.deviceAddress),
            it?.value?.ssdpPath
        )
    }
}

/**
 * Verify the device, in this case, we need to obtain the info block which
 * holds information such as the actual mac to use in certain scenarios.
 *
 * Without this mac (henceforth referred to as deviceID), we can't do multi-speaker
 * functions.
 *
 * @param deviceNetworkId The DNI of the device
 * @param ip The address of the device on the network (not the same as DNI)
 * @param port The port to use (0 will be treated as invalid and will use 80)
 * @param devicessdpPath The URL path (for example, /desc)
 *
 * @note Result is captured in setupHandler()
 */
private verifyDevice(String deviceNetworkId, String ip, int port, String devicessdpPath) {
    if(ip) {
        def address = ip + ":" + bosePort
        sendHubCommand(new physicalgraph.device.HubAction([
            method: "GET",
            path: "/info",
            headers: [
                HOST: address,
            ]], deviceNetworkId, [callback: "setupHandler"]))
    } else {
        log.warn("verifyDevice() IP address was empty")
    }
}

void setupHandler(hubResponse) {
    String contentType = hubResponse?.headers['Content-Type']
    if (contentType != null && contentType == 'text/xml') {
        def body = hubResponse.xml

        def deviceID = body.attributes()['deviceID']
        def device = getDevices().find {it?.key?.contains(deviceID)}
        if (device && !device.value?.verified) {
            device.value << [name:body?.name?.text(), model:body?.type?.text(), serialNumber:body?.serialNumber?.text(), "deviceID":deviceID, manufacturer: "Bose Corporation", verified: true]
        }
    }
}

/**
 * Returns an array of devices which have been verified
 *
 * @return array of verified devices
 */
def getVerifiedDevices() {
    getDevices().findAll{ it?.value?.verified == true }
}

/**
 * Returns all discovered devices or an empty array if none
 *
 * @return array of devices
 */
def getDevices() {
    state.devices = state.devices ?: [:]
}

/**
 * Converts a hexadecimal string to an integer
 *
 * @param hex The string with a hexadecimal value
 * @return An integer
 */
private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

/**
 * Converts an IP address represented as 0xAABBCCDD to AAA.BBB.CCC.DDD
 *
 * @param hex Address represented in hex
 * @return String containing normal IPv4 dot notation
 */
private String convertHexToIP(hex) {
    if (hex)
        [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
    else
        hex
}
