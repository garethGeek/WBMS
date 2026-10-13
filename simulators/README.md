# WBMS Weighbridge Indicator Simulator

This simulator behaves like a TCP/IP weighbridge indicator for presentations and local testing.
It streams simple Mettler Toledo-style weight lines that WBMS can parse and capture.
When it starts, it automatically runs a continuous vehicle cycle: 0 kg, rising weight, stable full weight, then back to 0 kg.

## Run

```powershell
node .\simulators\weighbridge-indicator-simulator.js 2016
```

Or choose another port and full vehicle weight:

```powershell
node .\simulators\weighbridge-indicator-simulator.js 8080 32480
```

To simulate configurable multi-cell values, provide the values in the same order as the station plates/cells:

```powershell
$env:SIM_MULTI_CELL = "true"
$env:SIM_CELL_WEIGHTS = "565,300,600,100"
$env:SIM_FULL_WEIGHT = "1565"
node .\simulators\weighbridge-indicator-simulator.js 8080
```

## Register In WBMS

Use TCP/IP connection type.

For the same PC:

```text
IP: 127.0.0.1
Port: 2016
```

For another PC on the same network:

```powershell
ipconfig
```

Use this PC's IPv4 address and the simulator port.

## Terminal Commands

Inside the simulator terminal:

```text
32480       set stable 32480 kg
s 32480    set stable 32480 kg
u 8500     set unstable 8500 kg
demo       restart vehicle cycle
zero       set 0 kg stable
clients    show connected clients
quit       stop simulator
```

## Example Output

```text
ST,GS,+0032480 kg
US,GS,+0008500 kg
ST,GS,+0000000 kg
```

Multi-cell example:

```text
00, 00, 00, 100
```
