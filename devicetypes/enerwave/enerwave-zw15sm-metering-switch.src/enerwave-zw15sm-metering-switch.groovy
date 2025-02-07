/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CON  DITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  2019-11-24 Bringing in repo from https://github.com/lgkahn/SmartThingsPublic/blob/MSA-752-6/devicetypes/smartthings/enerwave-zw15sm-metering-switch.src/enerwave-zw15sm-metering-switch.groovy
 *  2019-11-24 Formatting code
 */

metadata {
	definition (name: "Enerwave ZW15SM Metering Switch", namespace: "enerwave", author: "Sean Forsberg") {
		capability "Energy Meter"
		capability "Actuator"
		capability "Switch"
		capability "Power Meter"
		capability "Polling"
		capability "Refresh"
		capability "Configuration"
		capability "Sensor"
		capability "Indicator"
	   
		fingerprint inClusters: "0x25,0x32"
	}


	preferences {
		input("ReportTime", "number", title: "Report Timeout Interval?", description: "The time in minutes after which an update is sent?", defaultValue: 3, required: false)
		input("WattageChange", "number", title: "Wattage change before reporting: 1-25?", description: "The minimum wattage change before reporting?",defaultValue: 15, required: false)
		input("SyncLedWithPower", "number", title: "Sync LED With Power?: 0:opposite, 1:Synced", description: "Sync LED With Power?",defaultValue: 0, required: false)		
	}

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		for (int i = 0; i <= 10000; i += 1000) {
			status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
		}
		for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
		}

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"

	}

	// tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
		}
		valueTile("power", "device.power") {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy") {
			state "default", label:'${currentValue} kWh'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset kWh', action:"reset"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main(["switch","power","energy"])
		details(["switch","power","energy","indicatorStatus","refresh","reset"])
	}
}


def parse(String description) {
	def result = null
	//log.debug "in parse desc = $description"
	
	if(description == "updated") return 
	def cmd = zwave.parse(description, [0x20: 1, 0x32: 1, 0x72: 2])
	if (cmd) {
		result = zwaveEvent(cmd)
	}
	return result
}



def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
	//log.debug "in meter report cmd = $cmd "
	if (cmd.scale == 0) {
		log.debug "got energy/kWh = $cmd.scaledMeterValue"
		createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
	} else if (cmd.scale == 1) {
		createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
	} else if (cmd.scale == 2) {
		log.debug "got power/W = $cmd.scaledMeterValue"
		createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	def evt = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
	if (evt.isStateChange) {
		[evt, response(["delay 3000", zwave.meterV2.meterGet(scale: 2).format()])]
	} else {
		evt
	}
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	log.debug "in config report"
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "in manuf specific report"
	def result = []
	
	result << response(delayBetween([
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format(),
	]))

	result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "$device.displayName: Unhandled: $cmd"
	[:]
}

def on() {
	log.debug "in on"
	[
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}

def off() {
	log.debug "in off"
	[
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}

def poll() {
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
}

def refresh() {
	log.debug "in refresh"	
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
	
  
}

def configure() {
	log.debug "In configure"
	
	if (settings.WattageChange < 1 || settings.WattageChange > 25) {
		settings.WattageChange = 5;
	}
	
	if (settings.ReportTime == null)
		settings.ReportTime = 5

	if (settings.WattageChange == null)
		settings.WattageChnage = 10;
	
	if (settings.SyncLedWithPower < 0 || settings.SyncLedWithPower > 1) {
		settings.SyncLedWithPower = 1;
	}
	 
	log.debug "ReportTime: $settings.ReportTime, WattageChange: $settings.WattageChange, SyncLedWithPower: $settings.SyncLedWithPower"
	
	def wattageAdjust = settings.WattageChange * 10;
	
	delayBetween([
		// LED Control
		zwave.configurationV1.configurationSet(parameterNumber: 0x01, size: 1, scaledConfigurationValue: settings.SyncLedWithPower).format(),
		// Button Toggle Control
		zwave.configurationV1.configurationSet(parameterNumber: 0x02, size: 1, scaledConfigurationValue: 0).format(),
		//send meter report every x minutes.
		zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: settings.ReportTime).format(),
		// dont send multilevel report
		zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: 0).format(),
		//accumulated energy report meter interval
		zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: settings.ReportTime).format(),
		//send only meter report when wattage change 1=meter, 2=multilevel , 3=both, 0=none 
		zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 1).format(),
		//Minimum change in wattage (0.0,25.5) 0-255
		zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: wattageAdjust).format(),
	])
}

def reset() {
	log.debug "in reset"
	return [
		zwave.meterV2.meterReset().format(),
		zwave.meterV2.meterGet(scale: 0).format()
	]
}

def updated() {
	log.debug "in updated"
	configure();
}