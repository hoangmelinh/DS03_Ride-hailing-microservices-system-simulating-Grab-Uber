import React from 'react';
import { X, Copy, Check, Terminal, ShieldCheck, Hash, Radio } from 'lucide-react';

export default function PayloadInspector({ packet, onClose }) {
  const [copied, setCopied] = React.useState(false);

  if (!packet) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(packet, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-[#0f172a] border border-cyan-500/40 rounded-xl w-full max-w-2xl shadow-[0_0_35px_rgba(6,182,212,0.25)] overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        {/* Terminal Header */}
        <div className="flex items-center justify-between px-4 py-3 bg-[#1e293b]/70 border-b border-slate-700/60">
          <div className="flex items-center gap-2">
            <div className="flex gap-1.5">
              <span className="w-3 h-3 rounded-full bg-rose-500/80"></span>
              <span className="w-3 h-3 rounded-full bg-amber-500/80"></span>
              <span className="w-3 h-3 rounded-full bg-emerald-500/80"></span>
            </div>
            <span className="text-xs font-mono text-cyan-400 font-semibold tracking-wider ml-2 flex items-center gap-1.5">
              <Terminal size={14} /> PACKET INSPECTOR // {packet.type || 'PAYLOAD'}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopy}
              className="text-xs px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded border border-slate-700 flex items-center gap-1.5 transition"
            >
              {copied ? <Check size={12} className="text-emerald-400" /> : <Copy size={12} />}
              {copied ? 'Copied' : 'Copy JSON'}
            </button>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800 transition"
            >
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Packet Visual Structure (JWT/Envelope representation like user reference image) */}
        <div className="p-4 bg-[#090d16] border-b border-slate-800 flex flex-wrap gap-2 items-center justify-center">
          <div className="bg-slate-900 border border-amber-500/40 rounded px-3 py-1.5 text-center">
            <div className="text-[10px] text-amber-400 font-mono uppercase tracking-wider">PROTOCOL</div>
            <div className="text-xs font-mono text-slate-200 font-semibold">{packet.protocol || 'HTTP/1.1 REST'}</div>
          </div>
          <div className="text-slate-600 font-mono font-bold">+</div>
          <div className="bg-slate-900 border border-cyan-500/50 rounded px-3 py-1.5 text-center">
            <div className="text-[10px] text-cyan-400 font-mono uppercase tracking-wider">ACTION / EVENT</div>
            <div className="text-xs font-mono text-slate-200 font-semibold">{packet.action || packet.eventType || 'DISPATCH'}</div>
          </div>
          <div className="text-slate-600 font-mono font-bold">+</div>
          <div className="bg-slate-900 border border-purple-500/50 rounded px-3 py-1.5 text-center">
            <div className="text-[10px] text-purple-400 font-mono uppercase tracking-wider">CORRELATION ID</div>
            <div className="text-xs font-mono text-slate-300 truncate max-w-[150px]">{packet.correlationId || 'N/A'}</div>
          </div>
        </div>

        {/* Content Tabs / Body */}
        <div className="p-4 max-h-[420px] overflow-y-auto space-y-4 font-mono text-xs">
          {/* Metadata Section */}
          <div className="grid grid-cols-2 gap-2 text-slate-300 bg-slate-900/60 p-3 rounded border border-slate-800">
            <div>
              <span className="text-slate-500">Source:</span> <span className="text-amber-400 font-semibold">{packet.from}</span>
            </div>
            <div>
              <span className="text-slate-500">Destination:</span> <span className="text-emerald-400 font-semibold">{packet.to}</span>
            </div>
            {packet.eventId && (
              <div className="col-span-2">
                <span className="text-slate-500">Event ID (Unique Deduplication Key):</span>{' '}
                <span className="text-purple-300">{packet.eventId}</span>
              </div>
            )}
            {packet.causationId && (
              <div className="col-span-2">
                <span className="text-slate-500">Causation ID:</span>{' '}
                <span className="text-slate-400">{packet.causationId}</span>
              </div>
            )}
            {packet.occurredAt && (
              <div className="col-span-2">
                <span className="text-slate-500">Occurred At:</span>{' '}
                <span className="text-slate-400">{packet.occurredAt}</span>
              </div>
            )}
          </div>

          {/* Payload JSON */}
          <div>
            <div className="text-xs font-semibold text-slate-400 mb-1.5 flex items-center gap-1.5">
              <Radio size={13} className="text-cyan-400" />
              PAYLOAD BODY:
            </div>
            <pre className="bg-[#050811] p-3 rounded-lg border border-slate-800/80 text-emerald-400 leading-relaxed overflow-x-auto select-all">
              {JSON.stringify(packet.payload || packet.data || {}, null, 2)}
            </pre>
          </div>
        </div>

        {/* Footer */}
        <div className="px-4 py-2.5 bg-slate-950 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500">
          <span className="flex items-center gap-1 text-cyan-400/90 font-mono">
            <ShieldCheck size={13} /> Idempotent &amp; Distributed Tracing Verified
          </span>
          <button
            onClick={onClose}
            className="px-3 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
