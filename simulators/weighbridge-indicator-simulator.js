const net = require('net');
const readline = require('readline');

const host = process.env.SIM_HOST || '0.0.0.0';
const port = Number(process.env.SIM_PORT || process.argv[2] || 2016);
const intervalMs = Number(process.env.SIM_INTERVAL_MS || 1000);
const fullWeightKg = Number(process.env.SIM_FULL_WEIGHT || process.argv[3] || 32480);
const multiCell = /^(1|true|yes|multi)$/i.test(process.env.SIM_MULTI_CELL || '');
const multiCellWeights = (process.env.SIM_CELL_WEIGHTS || '')
  .split(',')
  .map(value => Number(value.trim()))
  .filter(value => Number.isFinite(value));

if (multiCell && multiCellWeights.length === 0) {
  throw new Error('SIM_CELL_WEIGHTS is required in multi-cell mode, for example 565,300,600,100');
}

let currentWeightKg = Number(process.env.SIM_START_WEIGHT || 0);
let stable = true;
let sequenceTimer = null;
const clients = new Set();

function formatReading(weightKg) {
  if (multiCell) {
    const referenceTotal = multiCellWeights.reduce((sum, value) => sum + value, 0);
    const scale = referenceTotal > 0 ? weightKg / referenceTotal : 0;
    return multiCellWeights
      .map(value => String(Math.round(value * scale)).padStart(2, '0'))
      .join(', ');
  }
  const status = stable ? 'ST' : 'US';
  const sign = weightKg < 0 ? '-' : '+';
  const absolute = Math.abs(Math.round(weightKg)).toString().padStart(7, '0');
  return `${status},GS,${sign}${absolute} kg`;
}

function sendTo(client, line) {
  if (!client.destroyed) {
    client.write(`${line}, `);
  }
}

function broadcast(line) {
  for (const client of clients) {
    sendTo(client, line);
  }
}

function setWeight(weightKg, isStable = true) {
  currentWeightKg = weightKg;
  stable = isStable;
  const line = formatReading(currentWeightKg);
  console.log(`reading -> ${line}`);
  broadcast(line);
}

function stopSequence() {
  if (sequenceTimer) {
    clearInterval(sequenceTimer);
    sequenceTimer = null;
  }
}

function startDemoSequence() {
  stopSequence();
  const steps = buildVehicleCrossingProfile(currentWeightKg > 0 ? currentWeightKg : fullWeightKg);

  let index = 0;
  console.log('vehicle cycle started');
  setWeight(steps[index], true);

  sequenceTimer = setInterval(() => {
    index = (index + 1) % steps.length;
    if (index === 0) {
      setWeight(0, true);
      return;
    }

    const isStable = index >= 24 && index <= 34 || index === steps.length - 1;
    setWeight(steps[index], isStable);
  }, 500);
}

function buildVehicleCrossingProfile(targetWeightKg) {
  const profile = [];
  for (let i = 0; i < 6; i += 1) profile.push(0);

  for (let i = 1; i <= 18; i += 1) {
    const progress = i / 18;
    profile.push(Math.round(targetWeightKg * easeInOut(progress)));
  }

  for (let i = 0; i < 12; i += 1) {
    const vibration = i % 2 === 0 ? 0 : Math.min(40, targetWeightKg * 0.001);
    profile.push(Math.round(targetWeightKg + vibration));
  }

  for (let i = 17; i >= 0; i -= 1) {
    const progress = i / 18;
    profile.push(Math.round(targetWeightKg * easeInOut(progress)));
  }

  return profile;
}

function easeInOut(progress) {
  return progress < 0.5
    ? 2 * progress * progress
    : 1 - ((-2 * progress + 2) ** 2) / 2;
}

const server = net.createServer((socket) => {
  const remote = `${socket.remoteAddress}:${socket.remotePort}`;
  clients.add(socket);
  console.log(`client connected: ${remote} | clients=${clients.size}`);
  sendTo(socket, formatReading(currentWeightKg));

  socket.on('close', () => {
    clients.delete(socket);
    console.log(`client disconnected: ${remote} | clients=${clients.size}`);
  });

  socket.on('error', (error) => {
    clients.delete(socket);
    console.log(`client error: ${remote} | ${error.message}`);
  });
});

server.listen(port, host, () => {
  console.log(`WBMS weighbridge indicator simulator listening on ${host}:${port}`);
  console.log(`format: ${multiCell ? `MULTI_CELL (${multiCellWeights.length} values)` : 'SINGLE_CELL'}`);
  console.log('Register a TCP/IP device in WBMS using this PC IP and the simulator port.');
  console.log('');
  console.log('Commands:');
  console.log('  number        set weight, example: 32480');
  console.log('  s number      set stable weight, example: s 32480');
  console.log('  u number      set unstable weight, example: u 8500');
  console.log('  demo          start/restart vehicle cycle');
  console.log('  zero          set 0 kg stable');
  console.log('  clients       show connected clients');
  console.log('  quit          stop simulator');
  console.log('');
  startDemoSequence();
});

setInterval(() => {
  broadcast(formatReading(currentWeightKg));
}, intervalMs);

function handleCommand(command, promptAgain) {
  if (!command) {
    promptAgain();
    return;
  }

  if (command === 'quit' || command === 'exit') {
    stopSequence();
    server.close();
    for (const client of clients) client.destroy();
    console.log('simulator stopped');
    process.exit(0);
  }

  if (command === 'demo') {
    startDemoSequence();
    promptAgain();
    return;
  }

  if (command === 'zero') {
    stopSequence();
    setWeight(0, true);
    promptAgain();
    return;
  }

  if (command === 'clients') {
    console.log(`connected clients: ${clients.size}`);
    promptAgain();
    return;
  }

  const stableMatch = command.match(/^s\s+(-?\d+(?:\.\d+)?)$/i);
  if (stableMatch) {
    stopSequence();
    setWeight(Number(stableMatch[1]), true);
    promptAgain();
    return;
  }

  const unstableMatch = command.match(/^u\s+(-?\d+(?:\.\d+)?)$/i);
  if (unstableMatch) {
    stopSequence();
    setWeight(Number(unstableMatch[1]), false);
    promptAgain();
    return;
  }

  if (/^-?\d+(\.\d+)?$/.test(command)) {
    stopSequence();
    setWeight(Number(command), true);
    promptAgain();
    return;
  }

  console.log(`unknown command: ${command}`);
  promptAgain();
}

if (process.stdin.isTTY) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: 'sim> ',
  });

  rl.prompt();
  rl.on('line', (input) => handleCommand(input.trim(), () => rl.prompt()));
  rl.on('close', () => handleCommand('quit', () => {}));
  process.on('SIGINT', () => rl.close());
} else {
  console.log('Running without interactive terminal input. Press Ctrl+C to stop.');
}
