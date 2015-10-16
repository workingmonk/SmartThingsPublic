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
    definition (name: "Incompatible Device", namespace: "smartthings", author: "SmartThings") {

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0004,0005,0009,0020,0101,0402,0B05,FDBD", outClusters: "000A,0019",
                manufacturer: "Kwikset", model: "SMARTCODE", deviceJoinName: "Kwikset Incomptabile Lock"

    }

    tiles(scale: 2) {
        valueTile("incompatibleShort", "device.incompatibleShort", width: 6, height: 3, inactiveLabel: false, decoration: "flat") {
            state "incompatible", label:'Error'
        }
        valueTile("incompatible", "device.incompatible", width: 6, height: 3, inactiveLabel: false, decoration: "flat") {
            state "incompatible", label:'Error. This device is \nincompatible with the \nSmartThings Platform. \nPlease see the list at \ncommunity.smartthings.com'
        }
        main "incompatibleShort"
        details(["incompatible"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
}
