import React from 'react';
import { 
  Radio, Zap, ShieldCheck, Database, Layers, Eye, 
  ArrowRight, Sparkles, Terminal, Cpu, Network, CheckCircle2 
} from 'lucide-react';

export default function FlowCanvas({ activeStep, activePacket, onInspectPacket, isPlaying }) {
  // Exact coordinate map inside SVG space (1160 x 420)
  const nodes = {
    passenger: {
      id: 'passenger',
      title: 'CLIENT: PASSENGER',
      subtitle: 'React / Web App',
      tag: 'ROLE:PASSENGER',
      color: 'amber',
      accent: '#f59e0b',
      x: 35,
      y: 40,
      w: 165,
      h: 80,
      inPort: { x: 35, y: 80 },
      outPort: { x: 200, y: 80 }
    },
    driverClient: {
      id: 'driverClient',
      title: 'CLIENT: DRIVER',
      subtitle: 'Mobile Device Simulator',
      tag: 'ROLE:DRIVER',
      color: 'blue',
      accent: '#38bdf8',
      x: 35,
      y: 280,
      w: 165,
      h: 80,
      inPort: { x: 35, y: 320 },
      outPort: { x: 200, y: 320 }
    },
    gateway: {
      id: 'gateway',
      title: 'API GATEWAY :8080',
      subtitle: 'Spring Cloud Gateway Edge',
      tag: 'JWT & TRACING',
      color: 'purple',
      accent: '#a855f7',
      x: 275,
      y: 160,
      w: 170,
      h: 90,
      inPort: { x: 275, y: 205 },
      outPort: { x: 445, y: 205 }
    },
    tripService: {
      id: 'tripService',
      title: 'TRIP SERVICE :8083',
      subtitle: 'PostgreSQL: goride_trip',
      tag: 'OUTBOX PRODUCER',
      color: 'cyan',
      accent: '#06b6d4',
      x: 520,
      y: 40,
      w: 170,
      h: 80,
      inPort: { x: 520, y: 80 },
      outPort: { x: 690, y: 80 },
      bottomPort: { x: 605, y: 120 }
    },
    matchingService: {
      id: 'matchingService',
      title: 'MATCHING SERVICE :8084',
      subtitle: 'Redis GEO + Saga Orchestrator',
      tag: 'REDIS GEO & SAGA',
      color: 'emerald',
      accent: '#10b981',
      x: 520,
      y: 165,
      w: 170,
      h: 80,
      inPort: { x: 520, y: 205 },
      outPort: { x: 690, y: 205 },
      topPort: { x: 605, y: 165 },
      bottomPort: { x: 605, y: 245 }
    },
    driverService: {
      id: 'driverService',
      title: 'DRIVER SERVICE :8082',
      subtitle: 'PostgreSQL: goride_driver',
      tag: 'ATOMIC RESERVATION',
      color: 'blue',
      accent: '#3b82f6',
      x: 520,
      y: 290,
      w: 170,
      h: 80,
      inPort: { x: 520, y: 330 },
      outPort: { x: 690, y: 330 },
      topPort: { x: 605, y: 290 }
    },
    rabbitmq: {
      id: 'rabbitmq',
      title: 'RABBITMQ BROKER',
      subtitle: 'goride.events (Topic)',
      tag: 'ASYNC MESSAGE BUS',
      color: 'amber',
      accent: '#f59e0b',
      x: 755,
      y: 160,
      w: 170,
      h: 90,
      inPort: { x: 755, y: 205 },
      outPort: { x: 925, y: 205 }
    },
    paymentService: {
      id: 'paymentService',
      title: 'PAYMENT SERVICE :8085',
      subtitle: 'PostgreSQL: goride_payment',
      tag: 'IDEMPOTENT PAY',
      color: 'pink',
      accent: '#ec4899',
      x: 985,
      y: 50,
      w: 170,
      h: 80,
      inPort: { x: 985, y: 90 },
      outPort: { x: 1155, y: 90 }
    },
    notificationService: {
      id: 'notificationService',
      title: 'NOTIFICATION SERVICE :8086',
      subtitle: 'PostgreSQL: goride_notification',
      tag: 'COMPOSITE DEDUP',
      color: 'yellow',
      accent: '#eab308',
      x: 985,
      y: 280,
      w: 170,
      h: 80,
      inPort: { x: 985, y: 320 },
      outPort: { x: 1155, y: 320 }
    }
  };

  // Mathematically exact lines between ports
  const pipelines = [
    {
      id: 'pass_to_gw',
      from: 'passenger',
      to: 'gateway',
      path: 'M 200 80 C 240 80, 235 205, 275 205'
    },
    {
      id: 'driver_to_gw',
      from: 'driverClient',
      to: 'gateway',
      path: 'M 200 320 C 240 320, 235 205, 275 205'
    },
    {
      id: 'gw_to_trip',
      from: 'gateway',
      to: 'tripService',
      path: 'M 445 205 C 485 205, 480 80, 520 80'
    },
    {
      id: 'trip_to_mq',
      from: 'tripService',
      to: 'rabbitmq',
      path: 'M 690 80 C 725 80, 720 205, 755 205'
    },
    {
      id: 'mq_to_match',
      from: 'rabbitmq',
      to: 'matchingService',
      path: 'M 755 205 L 690 205'
    },
    {
      id: 'match_to_driver',
      from: 'matchingService',
      to: 'driverService',
      path: 'M 605 245 L 605 290'
    },
    {
      id: 'match_to_trip',
      from: 'matchingService',
      to: 'tripService',
      path: 'M 605 165 L 605 120'
    },
    {
      id: 'driver_to_trip',
      from: 'driverClient',
      to: 'tripService',
      path: 'M 200 320 C 250 320, 420 80, 520 80'
    },
    {
      id: 'mq_to_pay',
      from: 'rabbitmq',
      to: 'paymentService',
      path: 'M 925 205 C 960 205, 950 90, 985 90'
    },
    {
      id: 'mq_to_notify',
      from: 'rabbitmq',
      to: 'notificationService',
      path: 'M 925 205 C 960 205, 950 320, 985 320'
    }
  ];

  // Match the active pipeline
  const activePipeline = pipelines.find(
    (p) =>
      activePacket &&
      ((activePacket.from === p.from && activePacket.to === p.to) ||
        (activePacket.from === p.to && activePacket.to === p.from))
  ) || pipelines[0];

  return (
    <div className="relative w-full bg-[#060a12] border border-slate-800/80 rounded-2xl overflow-hidden shadow-[0_0_50px_rgba(0,0,0,0.8)] select-none">
      {/* Top HUD Bar with Integrated Sleek Packet Capsule (Clean & NEVER blocks nodes!) */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 bg-[#0d1424]/90 border-b border-slate-800/80 backdrop-blur-md">
        {/* Left: Circuit Status */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-3 py-1 rounded-lg text-xs font-mono">
            <Radio size={13} className="text-emerald-400 animate-pulse" />
            <span className="text-slate-400 font-semibold">CIRCUIT PIPELINE:</span>
            <span className="text-emerald-400 font-bold">{isPlaying ? 'TRANSMITTING' : 'IDLE / STEPPED'}</span>
          </div>
        </div>

        {/* Center: The Sleek 3-Piece Packet Capsule (Docked in HUD, 100% visible, zero node blockage!) */}
        {activePacket && (
          <div
            onClick={() => onInspectPacket(activePacket)}
            className="cursor-pointer group hover:scale-105 transition-transform duration-200"
            title="Nhấp để soi chi tiết JSON Payload & Headers"
          >
            <div className="flex items-stretch rounded-xl overflow-hidden border border-cyan-400/80 bg-[#080f1e]/95 backdrop-blur-xl shadow-[0_0_20px_rgba(6,182,212,0.4)]">
              {/* Segment 1: Protocol */}
              <div className="px-3 py-1.5 bg-slate-900 border-r border-slate-800 flex items-center gap-2">
                <div className="grid grid-cols-2 gap-0.5 w-3 h-3">
                  <span className="w-1 h-1 bg-amber-400 rounded-full animate-ping"></span>
                  <span className="w-1 h-1 bg-amber-400 rounded-full"></span>
                  <span className="w-1 h-1 bg-amber-400 rounded-full"></span>
                  <span className="w-1 h-1 bg-amber-400 rounded-full"></span>
                </div>
                <div>
                  <div className="text-[8px] font-mono text-slate-500 uppercase font-bold">PROTOCOL</div>
                  <div className="text-xs font-mono font-bold text-amber-400">
                    {activePacket.protocol || 'HTTP/1.1'}
                  </div>
                </div>
              </div>

              {/* Segment 2: Action / Payload Key */}
              <div className="px-3.5 py-1.5 bg-[#0c162c] border-r border-slate-800 flex flex-col justify-center">
                <div className="text-[8.5px] font-mono text-cyan-400/90 font-semibold uppercase tracking-wider">
                  PAYLOAD // {activePacket.eventType || 'REST_CALL'}
                </div>
                <div className="text-xs font-mono font-bold text-white tracking-tight">
                  {activePacket.action || 'TRANSMIT'}
                </div>
              </div>

              {/* Segment 3: Signature Waveform */}
              <div className="px-3 py-1.5 bg-slate-900 flex items-center gap-1">
                <span className="inline-block w-1 h-2.5 bg-purple-400 rounded-full animate-pulse"></span>
                <span className="inline-block w-1 h-4 bg-purple-400 rounded-full"></span>
                <span className="inline-block w-1 h-2 bg-purple-400 rounded-full animate-bounce"></span>
                <span className="inline-block w-1 h-4.5 bg-purple-400 rounded-full"></span>
                <span className="inline-block w-1 h-2.5 bg-purple-400 rounded-full animate-pulse"></span>
              </div>

              {/* Inspector Pill Icon */}
              <div className="px-2.5 py-1.5 bg-cyan-950/60 border-l border-slate-800 text-cyan-300 flex items-center gap-1 text-[10px] font-mono font-semibold group-hover:bg-cyan-900/80 transition">
                <Eye size={12} />
                <span>SOI JSON</span>
              </div>
            </div>
          </div>
        )}

        {/* Right: Path Tracker */}
        {activePacket && (
          <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-3.5 py-1.5 rounded-lg text-xs font-mono text-cyan-300">
            <span className="font-bold text-white">{activePacket.from}</span>
            <ArrowRight size={13} className="text-cyan-400" />
            <span className="font-bold text-white">{activePacket.to}</span>
          </div>
        )}
      </div>

      {/* Main Single SVG Canvas (Cards + Lines inside the EXACT same coordinate space) */}
      <div className="relative w-full h-[410px] overflow-x-auto p-2">
        <svg
          viewBox="0 0 1190 400"
          className="w-full h-full min-w-[1100px]"
          style={{ display: 'block' }}
        >
          <defs>
            <linearGradient id="laserGrad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#06b6d4" />
              <stop offset="50%" stopColor="#3b82f6" />
              <stop offset="100%" stopColor="#a855f7" />
            </linearGradient>
            <filter id="laserGlow" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3.5" result="glow" />
              <feMerge>
                <feMergeNode in="glow" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <pattern id="cyberDots" x="0" y="0" width="24" height="24" patternUnits="userSpaceOnUse">
              <circle cx="2" cy="2" r="1" fill="#38bdf8" opacity="0.12" />
            </pattern>
          </defs>

          {/* Background Dot Grid */}
          <rect width="1190" height="400" fill="url(#cyberDots)" />

          {/* 1. All Static Background Pipelines */}
          {pipelines.map((pipe) => (
            <path
              key={pipe.id}
              d={pipe.path}
              fill="none"
              stroke="#1e293b"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
          ))}

          {/* 2. Active Glowing Laser Pipe & Traveling Comet */}
          {activePipeline && (
            <g>
              <path
                id="active-flow-path"
                d={activePipeline.path}
                fill="none"
                stroke="url(#laserGrad)"
                strokeWidth="4"
                filter="url(#laserGlow)"
                className="animate-pulse"
              />

              {/* Laser Traveling Comet Particle (0% -> 100% on active path) */}
              <circle r="7.5" fill="#06b6d4" filter="url(#laserGlow)">
                <animateMotion dur="1.2s" repeatCount="indefinite">
                  <mpath href="#active-flow-path" />
                </animateMotion>
              </circle>
              <circle r="3.5" fill="#ffffff">
                <animateMotion dur="1.2s" repeatCount="indefinite">
                  <mpath href="#active-flow-path" />
                </animateMotion>
              </circle>
            </g>
          )}

          {/* 3. Cards rendered directly inside SVG foreignObject (100% aligned with ports, ZERO obstruction!) */}
          {Object.values(nodes).map((node) => {
            const isActiveSource = activePacket?.from === node.id;
            const isActiveTarget = activePacket?.to === node.id;
            const isActive = isActiveSource || isActiveTarget;

            return (
              <g key={node.id}>
                {/* Visual Connection Port Dots on the Edge of Card */}
                {node.outPort && (
                  <circle
                    cx={node.outPort.x}
                    cy={node.outPort.y}
                    r="4"
                    fill={isActive ? '#06b6d4' : '#334155'}
                    stroke="#0f172a"
                    strokeWidth="1.5"
                  />
                )}
                {node.inPort && (
                  <circle
                    cx={node.inPort.x}
                    cy={node.inPort.y}
                    r="4"
                    fill={isActive ? '#06b6d4' : '#334155'}
                    stroke="#0f172a"
                    strokeWidth="1.5"
                  />
                )}

                {/* Card Container via foreignObject */}
                <foreignObject x={node.x} y={node.y} width={node.w} height={node.h}>
                  <div
                    xmlns="http://www.w3.org/1999/xhtml"
                    style={{ height: '100%' }}
                    className={`rounded-xl p-2.5 border transition-all duration-300 flex flex-col justify-between ${
                      isActive
                        ? 'border-cyan-400 bg-[#0d172c] shadow-[0_0_25px_rgba(6,182,212,0.5)] ring-2 ring-cyan-500/40'
                        : 'border-slate-800 bg-[#090e1c] hover:border-slate-700'
                    }`}
                  >
                    {/* Header */}
                    <div className="flex items-center justify-between pb-1 border-b border-slate-800">
                      <div className="flex items-center gap-1">
                        <span className={`w-1.5 h-1.5 rounded-full ${isActive ? 'bg-cyan-400 animate-ping' : 'bg-slate-600'}`} />
                        <span className="w-1.5 h-1.5 rounded-full bg-slate-600" />
                        <span className="w-1.5 h-1.5 rounded-full bg-slate-600" />
                      </div>
                      <span
                        style={{ backgroundColor: node.accent }}
                        className={`w-2 h-2 rounded-full ${isActive ? 'shadow-[0_0_8px_currentColor] animate-pulse' : 'opacity-60'}`}
                      />
                    </div>

                    {/* Content */}
                    <div>
                      <div className="text-[11px] font-mono font-bold text-slate-100 tracking-tight leading-tight">
                        {node.title}
                      </div>
                      <div className="text-[9px] font-mono text-slate-400 truncate mt-0.5">
                        {node.subtitle}
                      </div>
                    </div>

                    {/* Footer Tag */}
                    <div className="pt-1 border-t border-slate-800/80 flex items-center justify-between text-[8px] font-mono">
                      <span className="text-cyan-400 font-semibold">{node.tag}</span>
                      {isActive && <span className="text-amber-400 font-bold animate-pulse">ACTIVE</span>}
                    </div>
                  </div>
                </foreignObject>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}
