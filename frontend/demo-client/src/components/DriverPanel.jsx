import React from 'react';
import { 
  Power, Check, X, Play, Flag, User, Car, 
  ShieldCheck, AlertTriangle, Radio, Navigation2 
} from 'lucide-react';

export default function DriverPanel({
  driverStatus,
  onToggleStatus,
  currentTrip,
  onAcceptTrip,
  onRejectTrip,
  onStartTrip,
  onCompleteTrip
}) {
  const isOnline = driverStatus === 'AVAILABLE' || driverStatus === 'RESERVED' || driverStatus === 'ON_TRIP';

  return (
    <div className="bento-card p-5 h-full flex flex-col justify-between">
      <div className="space-y-4">
        {/* Panel Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/80">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center">
              <User size={16} className="text-cyan-400" />
            </div>
            <div>
              <h3 className="text-sm font-heading font-bold text-white tracking-wide">DRIVER CONSOLE</h3>
              <p className="text-[10px] font-mono text-slate-400">Atomic Reservation Invariant Owner</p>
            </div>
          </div>

          {/* Online/Offline Slider Switch */}
          <button
            onClick={onToggleStatus}
            className={`px-3 py-1.5 rounded-full text-xs font-mono font-bold flex items-center gap-1.5 transition ${
              isOnline
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/50 shadow-[0_0_15px_rgba(16,185,129,0.3)]'
                : 'bg-slate-900 text-slate-400 border border-slate-800'
            }`}
          >
            <Power size={13} className={isOnline ? 'text-emerald-400' : 'text-slate-500'} />
            {isOnline ? 'ONLINE (SẴN SÀNG)' : 'OFFLINE'}
          </button>
        </div>

        {/* Driver Profile Bio */}
        <div className="bg-[#090e1a] p-3.5 rounded-xl border border-slate-800/90 text-xs font-mono space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-slate-500 flex items-center gap-1.5">
              <User size={13} className="text-cyan-400" /> Tài xế:
            </span>
            <span className="text-slate-200 font-semibold">Lê Hải Đăng (d_dang_01)</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-slate-500 flex items-center gap-1.5">
              <Car size={13} className="text-amber-400" /> Phương tiện:
            </span>
            <span className="text-slate-300 font-medium">Honda City • 29A-888.88</span>
          </div>
          <div className="flex items-center justify-between border-t border-slate-800/80 pt-1.5">
            <span className="text-slate-500">Trạng thái Database:</span>
            <span className="text-cyan-400 font-bold px-2 py-0.5 rounded bg-slate-900 border border-slate-800">
              {driverStatus}
            </span>
          </div>
        </div>

        {/* Dispatch Notification Card */}
        {currentTrip && currentTrip.status !== 'CANCELLED' ? (
          <div className="bg-gradient-to-br from-[#121a2f] to-[#0a0f1d] border border-amber-500/50 rounded-xl p-3.5 space-y-3 text-xs font-mono shadow-[0_0_25px_rgba(245,158,11,0.15)]">
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <span className="text-amber-400 font-bold flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span> CUỐC MỚI ĐƯỢC GÁN
              </span>
              <span className="text-slate-400">{currentTrip.tripId}</span>
            </div>

            <div className="space-y-1.5 text-[11px] text-slate-300">
              <div>
                <span className="text-slate-500">Đón:</span> {currentTrip.pickupAddress}
              </div>
              <div>
                <span className="text-slate-500">Đến:</span> {currentTrip.dropoffAddress}
              </div>
              <div className="text-emerald-400 font-bold text-xs pt-1">
                Cước nhận được: {Number(currentTrip.estimatedFare || 45000).toLocaleString()} VND
              </div>
            </div>

            {/* Lifecycle Action Buttons */}
            <div className="pt-2 border-t border-slate-800/80 space-y-2">
              {(currentTrip.status === 'DRIVER_ASSIGNED' || currentTrip.status === 'MATCHING') && (
                <div className="grid grid-cols-2 gap-2">
                  <button
                    onClick={onAcceptTrip}
                    className="py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-lg flex items-center justify-center gap-1.5 shadow-[0_0_15px_rgba(16,185,129,0.3)] transition active:scale-[0.98]"
                  >
                    <Check size={14} /> Nhận cuốc (Accept)
                  </button>
                  <button
                    onClick={onRejectTrip}
                    className="py-2.5 bg-rose-950/80 hover:bg-rose-900 text-rose-300 font-bold rounded-lg border border-rose-700/60 flex items-center justify-center gap-1.5 transition active:scale-[0.98]"
                    title="Kích hoạt Saga Compensation: Tự động gửi RELEASE_DRIVER"
                  >
                    <X size={14} /> Từ chối (Rollback)
                  </button>
                </div>
              )}

              {currentTrip.status === 'ACCEPTED' && (
                <button
                  onClick={onStartTrip}
                  className="w-full py-2.5 bg-cyan-600 hover:bg-cyan-500 text-white font-bold rounded-lg flex items-center justify-center gap-1.5 shadow-[0_0_15px_rgba(6,182,212,0.3)] transition active:scale-[0.98]"
                >
                  <Play size={14} /> Bắt đầu đi (Start Trip)
                </button>
              )}

              {currentTrip.status === 'IN_PROGRESS' && (
                <button
                  onClick={onCompleteTrip}
                  className="w-full py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-bold rounded-lg flex items-center justify-center gap-1.5 shadow-[0_0_20px_rgba(168,85,247,0.4)] transition active:scale-[0.98]"
                >
                  <Flag size={14} /> Hoàn thành chuyến (Complete)
                </button>
              )}

              {currentTrip.status === 'COMPLETED' && (
                <div className="text-center py-2 bg-emerald-950/40 text-emerald-400 border border-emerald-500/30 rounded-lg font-semibold flex items-center justify-center gap-1.5">
                  <ShieldCheck size={15} /> Chuyến đi hoàn tất &bull; Đã trigger Payment/Notify
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="border border-dashed border-slate-800 rounded-xl p-8 text-center text-xs font-mono text-slate-500 space-y-2">
            <Radio size={20} className="mx-auto text-slate-600 animate-pulse" />
            <div>{isOnline ? 'Đang đợi Matching Saga phân bổ cuốc xe...' : 'Tài xế đang Offline. Bật Online để nhận cuốc.'}</div>
          </div>
        )}
      </div>

      <div className="pt-3 text-[10px] font-mono text-slate-500 text-center flex items-center justify-center gap-1.5 border-t border-slate-800/80">
        <ShieldCheck size={13} className="text-emerald-400" />
        Atomic Reservation: Enforced at Database Level
      </div>
    </div>
  );
}
