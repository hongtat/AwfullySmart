/**
*  NEO Coolcam Light Switch - Device Handler (parent)
*
*  Copyright 2018 HongTat Tan
*
*
*  Version history:
*      1.0 (29/09/2018) - Initial Release
*
*
*  Requires ** NEO Coolcam Light Switch Child Device **
*
*  Tested on the following Z-Wave light switches:
*      NEO Coolcam Z-Wave Light Switch (EU-1/2 Gang)
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
	definition(name: "NEO Coolcam Light Switch", namespace: "hongtat", author: "HongTat Tan", mnmn: "SmartThings", vid: "generic-switch") {
		capability "Actuator"
		capability "Health Check"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Configuration"

		attribute "lastCheckin", "String"

		// This DTH uses 2 switch endpoints. Parent DTH controls endpoint 1 so please use '1' at the end of deviceJoinName
		// Child device (isComponent : false) representing endpoint 2 will substitute 1 with 2 for easier identification.
		fingerprint mfr: "0258", prod: "0003", model: "008B", deviceJoinName: "NEO Light Switch 1" // US - 2-CH
		fingerprint mfr: "0258", prod: "0003", model: "108B", deviceJoinName: "NEO Light Switch 1" // EU - 2-CH

		fingerprint mfr: "0258", prod: "0003", model: "008C", deviceJoinName: "NEO Light Switch" // US - 1-CH
		fingerprint mfr: "0258", prod: "0003", model: "108C", deviceJoinName: "NEO Light Switch" // EU - 1-CH
	}

	simulator {
	}

	preferences {
        section(title: "Check-in Interval") {
            paragraph "Run a Check-in procedure every so often."
            input("checkin", "enum", title: "Run Check-in procedure", options: ["Every 1 minute", "Every 5 minutes", "Every 10 minutes", "Every 15 minutes", "Every 30 minutes", "Every 1 hour"], description: "Allows check-in procedure to run every so often", defaultValue: "Every 1 minute", required: false, displayDuringSetup: true)
        }
        section(title: "Check-in Info") {
            paragraph "Display check-in info"
            input("checkinInfo", "enum", title: "Show last Check-in info", options: ["Hide", "MM/dd/yyyy h:mm", "MM-dd-yyyy h:mm", "dd/MM/yyyy h:mm", "dd-MM-yyyy h:mm"], description: "Show last check-in info.", defaultValue: "dd/MM/yyyy h:mm", required: false, displayDuringSetup: true)
        }
		section(title: "Configurations") {
            paragraph "Configurations"
            input("backLightOnOff", "enum", title: "Enable Back Light?", options: ["Yes", "No"], defaultValue: "Yes", required: false, displayDuringSetup: true)
			input("relayOnOff", "enum", title: "Enable Relay ON/OFF indicator?", options: ["Yes", "No"], defaultValue: "Yes", required: false, displayDuringSetup: true)
			input("rememberState", "enum", title: "Remember light state?", options: ["Yes", "No"], defaultValue: "Yes", required: false, displayDuringSetup: true)
        }
    }

	// tile definitions
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
	def componentLabel
	// 2-CH
	if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
		if (device.displayName.endsWith('1')) {
			componentLabel = "${device.displayName[0..-2]}2"
		} else {
			componentLabel = "$device.displayName 2"
		}
		try {
			String dni = "${device.deviceNetworkId}-ep2"
			addChildDevice("NEO Coolcam Light Switch Child", dni, null,
				[completedSetup: true, label: "${componentLabel}",
				 isComponent: false, componentName: "ch2", componentLabel: "${componentLabel}"])
			log.debug "Endpoint 2 (NEO Coolcam Light Switch Child) added as $componentLabel"
		} catch (e) {
			log.warn "Failed to add endpoint 2 ($desc) as NEO Coolcam Light Switch Child - $e"
		}
	}
	configure()
}

def updated() {
	configure()
}

def configure() {
	def checkinMethod = (settings.checkin ?: 'Every 1 minute').replace('Every ', 'Every').replace(' minute', 'Minute').replace(' hour', 'Hour')
    try {
        "run$checkinMethod"(refresh)
    } catch (all) { }

	// Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
	sendEvent(name: "checkInterval", value: 30 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	def commands = []
	if (zwaveInfo.mfr.equals("0258")) {
		// 1-CH
		if (zwaveInfo.model.equals("008C") || zwaveInfo.model.equals("108C")) {
			commands << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, configurationValue: [(rememberState == "Yes") ? 1 : 0]).format()
			commands << zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, configurationValue: [(backLightOnOff == "Yes") ? 1 : 0]).format()
			commands << zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, configurationValue: [(relayOnOff == "Yes") ? 1 : 0]).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId: []).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId]).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationGet(groupingIdentifier: 1).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: [zwaveHubNodeId]).format()
			commands << "delay 100"
			commands << zwave.associationV2.associationGet(groupingIdentifier: 2).format()
			commands << "delay 100"
		}
		// 2-CH
		if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
			commands << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, configurationValue: [(backLightOnOff == "Yes") ? 1 : 0]).format()
			commands << zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, configurationValue: [(relayOnOff == "Yes") ? 1 : 0]).format()
			commands << zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, configurationValue: [(rememberState == "Yes") ? 1 : 0]).format()
			commands << zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, configurationValue: [0]).format()
			commands << "delay 100"
			commands << zwave.multiChannelAssociationV2.multiChannelAssociationRemove(groupingIdentifier: 1, nodeId: []).format()
			commands << "delay 100"
        	commands << zwave.multiChannelAssociationV2.multiChannelAssociationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId]).format()
			commands << "delay 100"
			commands << zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: 1).format()
			commands << "delay 100"
		}
	}
	response(commands)
}

def parse(String description) {
	def checkinInfoFormat = (settings.checkinInfo ?: 'dd/MM/yyyy h:mm')
    def now = ''
    if (checkinInfoFormat != 'Hide') {
        try {
            now = 'Last Check-in: ' + new Date().format("${checkinInfoFormat}a", location.timeZone)
        } catch (all) { }
    }
    sendEvent(name: "lastCheckin", value: now, displayed: false)
	if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
		def childDevice = childDevices.find{ it.deviceNetworkId == "$device.deviceNetworkId-ep2" }
		if (childDevice) {
			childDevice.sendEvent(name: "lastCheckin", value: now, displayed: false)
		}
	}
	def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x27: 1, 0x84: 1, 0x98: 1, 0x56: 1, 0x60: 3])
	if (cmd) {
		result = zwaveEvent(cmd)
		log.debug "Parsed ${description} to ${result.inspect()}"
	} else {
        log.debug "Non-parsed event: ${description}"
    }
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd, endpoint=null) {
	if (zwaveInfo.model.equals("008C") || zwaveInfo.model.equals("108C")) {
		createEvent(name:"switch", value: cmd.value ? "on" : "off")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd, endpoint=null) {
	if (endpoint == 1 || endpoint == null) {
		createEvent(name:"switch", value: cmd.value ? "on" : "off")
	} else {
		def childDevice = childDevices.find{ it.deviceNetworkId == "$device.deviceNetworkId-ep$endpoint" }
		if (childDevice) {
			childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off")
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, endpoint=null) {
	if (endpoint == 1 || endpoint == null) {
		createEvent(name:"switch", value: cmd.value ? "on" : "off")
	} else {
		def childDevice = childDevices.find{ it.deviceNetworkId == "$device.deviceNetworkId-ep$endpoint" }
		if (childDevice) {
			childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off")
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand()
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		sendEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x32: 3, 0x25: 1, 0x20: 1])
	zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint)
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def versions = [0x31: 2, 0x30: 1, 0x84: 1, 0x9C: 1, 0x70: 2]
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
	[:]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	[descriptionText: "$device.displayName: $cmd", isStateChange: true]
}

def on() {
	// 1-CH
	if (zwaveInfo.model.equals("008C") || zwaveInfo.model.equals("108C")) {
		def cmds = []
        cmds << new physicalgraph.device.HubAction(zwave.basicV1.basicSet(value: 0xFF).format())
        cmds << new physicalgraph.device.HubAction(zwave.switchBinaryV1.switchBinaryGet().format())
        sendHubCommand(cmds, 500)
	}
	// 2-CH
	if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
		// parent DTH controls endpoint 1
		def endpointNumber = 1
		delayBetween([
			encap(endpointNumber, zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF)),
			encap(endpointNumber, zwave.switchBinaryV1.switchBinaryGet())
		])
	}
}

def off() {
	// 1-CH
	if (zwaveInfo.model.equals("008C") || zwaveInfo.model.equals("108C")) {
		def cmds = []
        cmds << new physicalgraph.device.HubAction(zwave.basicV1.basicSet(value: 0x00).format())
        cmds << new physicalgraph.device.HubAction(zwave.switchBinaryV1.switchBinaryGet().format())
        sendHubCommand(cmds, 500)
	}
	// 2-CH
	if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
		// parent DTH controls endpoint 1
		def endpointNumber = 1
		delayBetween([
			encap(endpointNumber, zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00)),
			encap(endpointNumber, zwave.switchBinaryV1.switchBinaryGet())
		])
	}
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	refresh()
}

def refresh() {
	def lastRefreshed = state.lastRefreshed
	if (lastRefreshed && (now() - lastRefreshed < 5000)) return
	state.lastRefreshed = now()

	if (zwaveInfo.mfr.equals("0258")) {
		if (zwaveInfo.model.equals("008B") || zwaveInfo.model.equals("108B")) {
	        def commands = (1..2).collect { endpoint ->
	            new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:endpoint, destinationEndPoint:endpoint, commandClass:37, command:2).format())
	        }
			sendHubCommand(commands, 500)
		}
		if (zwaveInfo.model.equals("008C") || zwaveInfo.model.equals("108C")) {
			sendHubCommand([
	            new physicalgraph.device.HubAction(zwave.basicV1.basicGet().format()),
	            new physicalgraph.device.HubAction(zwave.switchBinaryV1.switchBinaryGet().format())
	        ], 500)
		}
	}
}
def onOffCmd(dni, value) {
    def cmds = []
    cmds << new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:channelNumber(dni), destinationEndPoint:channelNumber(dni), commandClass:37, command:1, parameter:[value]).format())
    cmds << new physicalgraph.device.HubAction(zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:channelNumber(dni), destinationEndPoint:channelNumber(dni), commandClass:37, command:2).format())
    sendHubCommand(cmds, 1000)
}

def encap(endpointNumber, cmd) {
	if (cmd instanceof physicalgraph.zwave.Command) {
		command(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpointNumber).encapsulate(cmd))
	} else if (cmd.startsWith("delay")) {
		cmd
	} else {
		def header = "600D00"
		String.format("%s%02X%s", header, endpointNumber, cmd)
	}
}

private command(physicalgraph.zwave.Command cmd) {
	if (zwaveInfo.zw.contains("s")) {
		secEncap(cmd)
	} else if (zwaveInfo.cc.contains("56")){
		crcEncap(cmd)
	} else {
		cmd.format()
	}
}

private secEncap(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
	zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private channelNumber(String dni) {
    if (dni.indexOf("-ep") >= 0) {
        dni.split("-ep")[-1] as Integer
    } else {
        "1" as Integer
    }
}
