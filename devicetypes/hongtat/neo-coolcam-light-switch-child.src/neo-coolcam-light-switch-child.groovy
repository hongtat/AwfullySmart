/**
 *  NEO Coolcam Light Switch - Device Handler (child)
 *
 *  Copyright 2018 HongTat Tan
 *
 *
 *  Version history:
 *      1.0 (29/09/2018) - Initial Release
 *
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
    definition (name: "NEO Coolcam Light Switch Child", namespace: "hongtat", author: "HongTat Tan", mnmn: "SmartThings", vid: "generic-switch") {
        capability "Actuator"
        capability "Health Check"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"

        attribute "lastCheckin", "String"
    }
    simulator {
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			}
			tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'${currentValue}')
            }
		}
		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
		main "switch"
		details(["switch", "refresh"])
    }
}
def installed() {
	configure()
}

def updated() {
	configure()
}
def configure() {
	// Device-Watch simply pings if no device events received for checkInterval duration of 32min
	sendEvent(name: "checkInterval", value: 30 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: parent.hubID])
	refresh()
}
def on() {
    parent.onOffCmd(device.deviceNetworkId, 0xFF)
}

def off() {
    parent.onOffCmd(device.deviceNetworkId, 0)
}
def refresh() {
    parent.refresh()
}
