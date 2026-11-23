import React, { useState } from 'react';
import { 
  MapPin, Navigation, DollarSign, Clock, Car, 
  CheckCircle2, AlertCircle, Sparkles, TrendingUp 
} from 'lucide-react';

const PRESET_ROUTES = [
  {
    name: 'ĐH Bách Khoa ➔ Hồ Gươm',
    distance: '3.2 km',
    duration: '8 mins',
    pickup: { address: '1 Đại Cồ Việt, Hai Bà Trưng, HN', lat: 21.0051, lng: 105.8432 },
    dropoff: { address: 'Hồ Hoàn Kiếm, Hoàn Kiếm, HN', lat: 21.0285, lng: 105.8542 }
  },
  {
    name: 'Sân bay Nội Bài ➔ Keangnam 72',
    distance: '24.5 km',
    duration: '35 mins',
    pickup: { address: 'Sân bay Quốc tế Nội Bài, HN', lat: 21.2187, lng: 105.8048 },
    dropoff: { address: 'Keangnam Landmark 72, Cầu Giấy, HN', lat: 21.0167, lng: 105.7836 }
  },
  {
    name: 'ĐH Quốc Gia ➔ Royal City',
    distance: '6.8 km',
    duration: '16 mins',
    pickup: { address: '144 Xuân Thủy, Cầu Giấy, HN', lat: 21.0381, lng: 105.7825 },
    dropoff: { address: '72A Nguyễn Trãi, Thanh Xuân, HN', lat: 20.9997, lng: 105.8152 }
  }
];

export default function PassengerPanel({
  onEstimate,
  onRequestRide,
  onCancelRide,
  currentTrip,
  isEstimating,
  isRequesting
}) {
  const [selectedRouteIdx, setSelectedRouteIdx] = useState(0);
  const route = PRESET_ROUTES[selectedRouteIdx];

  const getStatusBadge = (status) => {
    switch (status) {
      case 'REQUESTED':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/40 animate-pulse';
      case 'MATCHING':
        return 'bg-blue-500/20 text-blue-300 border-blue-500/40 animate-pulse';
      case 'DRIVER_ASSIGNED':
        return 'bg-purple-500/20 text-purple-300 border-purple-500/40';
      case 'ACCEPTED':
        return 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40';
      case 'IN_PROGRESS':
        return 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40 animate-pulse';
      case 'COMPLETED':
        return 'bg-emerald-500/30 text-emerald-200 border-emerald-500/50';
      case 'CANCELLED':
        return 'bg-rose-500/20 text-rose-300 border-rose-500/40';
      default:
        return 'bg-slate-800 text-slate-400 border-slate-700';
    }
  };

  return (
    <div className="bento-card p-5 h-full flex flex-col justify-between">
      <div className="space-y-4">
        {/* Panel Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/80">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-amber-500/20 border border-amber-500/30 flex items-center justify-center">
              <Car size={16} className="text-amber-400" />
            </div>
            <div>
              <h3 className="text-sm font-heading font-bold text-white tracking-wide">PASSENGER CONSOLE</h3>
              <p className="text-[10px] font-mono text-slate-400">Authenticated via JWT (ROLE_PASSENGER)</p>
            </div>
          </div>
          <span className="text-[10px] font-mono bg-slate-900 text-cyan-300 px-2 py-0.5 rounded border border-slate-800">
            u_hoang_01
          </span>
        </div>

        {/* Route Preset Tabs */}
        <div>
          <label className="text-[11px] font-mono text-slate-400 mb-1.5 flex items-center justify-between">
            <span>Chọn tuyến đường mẫu:</span>
            <span className="text-emerald-400 text-[10px] flex items-center gap-1">
              <TrendingUp size={11} /> Haversine + Surge 1.0x
            </span>
          </label>
          <div className="grid grid-cols-1 gap-1.5">
            {PRESET_ROUTES.map((r, i) => (
              <button
                key={i}
                onClick={() => setSelectedRouteIdx(i)}
                className={`w-full text-left p-2.5 rounded-lg border text-xs font-mono transition flex items-center justify-between ${
                  selectedRouteIdx === i
                    ? 'bg-cyan-950/40 border-cyan-500/60 text-white shadow-[0_0_15px_rgba(6,182,212,0.15)]'
                    : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                }`}
              >
                <span className="font-semibold">{r.name}</span>
                <span className="text-[10px] text-slate-500 font-mono">{r.distance} • {r.duration}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Pickup & Dropoff Visual Route Card */}
        <div className="bg-[#090e1a] border border-slate-800/90 rounded-xl p-3 space-y-2.5 text-xs font-mono">
          <div className="flex items-start gap-2.5">
            <div className="flex flex-col items-center mt-1">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 shadow-[0_0_8px_#10b981]" />
              <span className="w-0.5 h-6 bg-slate-700 my-0.5" />
              <span className="w-2.5 h-2.5 rounded-full bg-rose-400 shadow-[0_0_8px_#f43f5e]" />
            </div>
            <div className="flex-1 space-y-2 overflow-hidden">
              <div>
                <div className="text-[9.5px] text-slate-500 uppercase font-bold">ĐIỂM ĐÓN (PICKUP)</div>
                <div className="text-slate-200 truncate font-semibold">{route.pickup.address}</div>
              </div>
              <div>
                <div className="text-[9.5px] text-slate-500 uppercase font-bold">ĐIỂM ĐẾN (DROPOFF)</div>
                <div className="text-slate-200 truncate font-semibold">{route.dropoff.address}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Current Trip Status Telemetry */}
        {currentTrip && (
          <div className="bg-gradient-to-br from-[#0c162c] to-[#090e1a] border border-cyan-500/50 rounded-xl p-3.5 space-y-2 text-xs font-mono shadow-[0_0_20px_rgba(6,182,212,0.15)]">
            <div className="flex items-center justify-between">
              <span className="text-slate-400">Mã chuyến (Trip ID):</span>
              <span className="text-cyan-300 font-bold tracking-wider">{currentTrip.tripId}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-slate-400">Trạng thái vòng đời:</span>
              <span className={`px-2.5 py-0.5 rounded-full border text-[10.5px] font-bold ${getStatusBadge(currentTrip.status)}`}>
                {currentTrip.status}
              </span>
            </div>
            <div className="flex items-center justify-between pt-1 border-t border-slate-800/80">
              <span className="text-slate-400">Giá cước:</span>
              <span className="text-emerald-400 font-bold text-sm">
                {Number(currentTrip.estimatedFare || 45000).toLocaleString()} VND
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Action CTA Buttons */}
      <div className="pt-4 border-t border-slate-800/80 space-y-2.5">
        <button
          onClick={() => onEstimate(route)}
          disabled={isEstimating}
          className="w-full py-2 bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs font-mono font-semibold rounded-lg border border-slate-700 flex items-center justify-center gap-2 transition active:scale-[0.98]"
        >
          <DollarSign size={14} className="text-emerald-400" />
          {isEstimating ? 'Đang tính toán giá cước...' : 'Dự toán cước (Estimate Fare)'}
        </button>

        <div className="grid grid-cols-2 gap-2.5">
          <button
            onClick={() => onRequestRide(route)}
            disabled={isRequesting || (currentTrip && currentTrip.status !== 'COMPLETED' && currentTrip.status !== 'CANCELLED')}
            className="py-2.5 bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 disabled:opacity-50 text-white text-xs font-mono font-bold rounded-lg flex items-center justify-center gap-1.5 shadow-[0_0_20px_rgba(6,182,212,0.35)] transition active:scale-[0.98]"
          >
            <Car size={14} />
            {isRequesting ? 'Đang gửi...' : 'Đặt xe ngay'}
          </button>

          <button
            onClick={onCancelRide}
            disabled={!currentTrip || currentTrip.status === 'COMPLETED' || currentTrip.status === 'CANCELLED'}
            className="py-2.5 bg-rose-950/60 hover:bg-rose-900/80 disabled:opacity-40 text-rose-300 text-xs font-mono font-semibold rounded-lg border border-rose-800/60 flex items-center justify-center gap-1.5 transition active:scale-[0.98]"
          >
            <AlertCircle size={14} />
            Hủy chuyến
          </button>
        </div>
      </div>
    </div>
  );
}
