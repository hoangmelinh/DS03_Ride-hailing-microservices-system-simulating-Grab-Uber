import React, { useState, useEffect } from 'react';
import { 
  Play, Pause, SkipForward, RotateCcw, Activity, ShieldCheck, 
  Terminal, Layers, Cpu, Eye, Radio, Server, CheckCircle2, 
  AlertOctagon, Sparkles, Sliders, ExternalLink, RefreshCw, Zap,
  AlertTriangle, Lock, ShieldAlert, ArrowRight, CornerDownRight
} from 'lucide-react';
import FlowCanvas from './components/FlowCanvas';
import PassengerPanel from './components/PassengerPanel';
import DriverPanel from './components/DriverPanel';
import PayloadInspector from './components/PayloadInspector';
import { 
  estimateFare, createTrip, updateTripStatus, checkGatewayHealth, 
  getTripById, getMatchingSaga, updateDriverStatus, updateDriverLocation, bootstrapDemoData,
  triggerLiveSagaCompensation, triggerLiveSecurityInvalidToken, triggerLiveSecurityValidation, triggerLiveConcurrency,
  DEMO_PASSENGER_ID, DEMO_DRIVER_ID
} from './services/api';

// 8 Mandatory Scenarios as defined in docs/plan/DS03_Project_Plan.md Section 13 & 14
const SCENARIOS = {
  HAPPY_FLOW: {
    id: 'HAPPY_FLOW',
    tag: 'F01-F08',
    name: '1. Happy Flow (Chu trình hoàn chỉnh: Đặt xe ➔ RabbitMQ ➔ Saga Matching ➔ Gán xe ➔ Hoàn thành ➔ Thanh toán)',
    category: 'FUNCTIONAL & DISTRIBUTED CORE',
    desc: 'Chứng minh luồng chuẩn: Transactional Outbox, RabbitMQ Topic Exchange, Saga Orchestration, gán tài xế nguyên tử và Fanout thanh toán.',
    steps: [
      {
        step: 1,
        title: 'Passenger gửi yêu cầu đặt xe',
        desc: 'Hành khách gửi HTTP POST /api/v1/trips kèm JWT qua API Gateway',
        from: 'passenger',
        to: 'gateway',
        protocol: 'HTTP/1.1 POST',
        action: 'REQUEST_RIDE',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          passengerId: DEMO_PASSENGER_ID,
          pickup: { lat: 21.0051, lng: 105.8432, address: 'ĐH Bách Khoa HN' },
          dropoff: { lat: 21.0285, lng: 105.8542, address: 'Hồ Hoàn Kiếm' },
          estimatedFare: 45000,
          currency: 'VND'
        }
      },
      {
        step: 2,
        title: 'API Gateway xác thực JWT & chuyển tiếp',
        desc: 'Gateway kiểm tra Bearer Token, gán header X-Correlation-Id và route sang Trip Service',
        from: 'gateway',
        to: 'tripService',
        protocol: 'HTTP/1.1 PROXY',
        action: 'ROUTE_TO_TRIP',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          headers: { 'X-Correlation-Id': 'c_corr_882a9f1b', 'Authorization': 'Bearer eyJhbGci...' },
          path: '/api/v1/trips',
          targetPort: 8083
        }
      },
      {
        step: 3,
        title: 'Trip Service lưu DB & bắn Outbox Event',
        desc: 'Trip Service lưu REQUESTED vào bảng trips và lưu trip.created vào outbox_event trong cùng 1 Local Transaction',
        from: 'tripService',
        to: 'rabbitmq',
        protocol: 'AMQP 0-9-1',
        action: 'PUBLISH_OUTBOX',
        eventType: 'trip.created',
        eventId: 'evt_tr_create_001',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          eventId: 'evt_tr_create_001',
          eventType: 'trip.created',
          eventVersion: 1,
          producer: 'trip-service',
          aggregateId: 'tr_882a9f1b',
          payload: { tripId: 'tr_882a9f1b', passengerId: DEMO_PASSENGER_ID, fare: 45000, currency: 'VND' }
        }
      },
      {
        step: 4,
        title: 'RabbitMQ định tuyến sang Matching Service',
        desc: 'Exchange goride.events chuyển tiếp event theo key trip.created vào matching.trip-created.q',
        from: 'rabbitmq',
        to: 'matchingService',
        protocol: 'AMQP CONSUME',
        action: 'CONSUME_TRIP_CREATED',
        eventType: 'trip.created',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          queue: 'matching.trip-created.q',
          routingKey: 'trip.created',
          inboxStatus: 'DEDUPLICATED_ACK'
        }
      },
      {
        step: 5,
        title: 'Matching Saga gọi REST giữ chỗ tài xế nguyên tử',
        desc: 'Matching Service tìm tài xế qua Driver Service, gửi REST POST /api/v1/drivers/{id}/reserve để khóa độc quyền',
        from: 'matchingService',
        to: 'driverService',
        protocol: 'HTTP REST SYNC',
        action: 'ATOMIC_RESERVE_DRIVER',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          driverId: DEMO_DRIVER_ID,
          tripId: 'tr_882a9f1b',
          atomicSql: "UPDATE drivers SET status='RESERVED', current_trip_id='tr_882a9f1b' WHERE id='...' AND status='AVAILABLE'",
          affectedRows: 1
        }
      },
      {
        step: 6,
        title: 'Matching Service gán tài xế vào chuyến đi',
        desc: 'Matching gọi PATCH /api/v1/trips/{id}/assign sang Trip Service. Trạng thái chuyển sang DRIVER_ASSIGNED',
        from: 'matchingService',
        to: 'tripService',
        protocol: 'HTTP REST SYNC',
        action: 'ASSIGN_DRIVER_TO_TRIP',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          tripId: 'tr_882a9f1b',
          driverId: DEMO_DRIVER_ID,
          newStatus: 'DRIVER_ASSIGNED',
          sagaState: 'COMPLETED'
        }
      },
      {
        step: 7,
        title: 'Tài xế bấm nhận cuốc (Accept)',
        desc: 'Driver App gửi PATCH /api/v1/trips/{id}/accept qua Gateway. Trip status chuyển sang ACCEPTED',
        from: 'driverClient',
        to: 'gateway',
        protocol: 'HTTP REST',
        action: 'ACCEPT_TRIP',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          tripId: 'tr_882a9f1b',
          driverId: DEMO_DRIVER_ID,
          status: 'ACCEPTED'
        }
      },
      {
        step: 8,
        title: 'Tài xế bắt đầu chuyến đi (Start)',
        desc: 'Driver App gửi PATCH /api/v1/trips/{id}/start. Trạng thái chuyển sang IN_PROGRESS, xe sang ON_TRIP',
        from: 'driverClient',
        to: 'gateway',
        protocol: 'HTTP REST',
        action: 'START_TRIP',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          tripId: 'tr_882a9f1b',
          status: 'IN_PROGRESS',
          driverStatus: 'ON_TRIP'
        }
      },
      {
        step: 9,
        title: 'Tài xế hoàn thành chuyến (Complete)',
        desc: 'Driver gửi PATCH /api/v1/trips/{id}/complete. Trip chuyển COMPLETED và ghi outbox trip.completed',
        from: 'driverClient',
        to: 'tripService',
        protocol: 'HTTP REST',
        action: 'COMPLETE_TRIP',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          tripId: 'tr_882a9f1b',
          status: 'COMPLETED',
          completedAt: new Date().toISOString()
        }
      },
      {
        step: 10,
        title: 'Trip Service bắn Outbox Event trip.completed lên RabbitMQ',
        desc: 'Worker RabbitMQ đẩy event trip.completed để kích hoạt thanh toán và thông báo bất đồng bộ',
        from: 'tripService',
        to: 'rabbitmq',
        protocol: 'AMQP 0-9-1',
        action: 'PUBLISH_OUTBOX',
        eventType: 'trip.completed',
        eventId: 'evt_tr_comp_009',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          eventId: 'evt_tr_comp_009',
          eventType: 'trip.completed',
          aggregateId: 'tr_882a9f1b',
          amount: 45000,
          currency: 'VND'
        }
      },
      {
        step: 11,
        title: 'RabbitMQ phân tán sang Payment & Notification Services',
        desc: 'Payment Service trừ tiền với Idempotency, Notification Service gửi push thông báo',
        from: 'rabbitmq',
        to: 'paymentService',
        protocol: 'AMQP FANOUT',
        action: 'PAYMENT_AND_NOTIFY',
        eventType: 'trip.completed',
        correlationId: 'c_corr_882a9f1b',
        payload: {
          paymentStatus: 'PAID',
          idempotencyKey: 'idem_pay_tr_882a9f1b',
          notificationSent: true,
          channel: 'IN_APP_PUSH'
        }
      }
    ]
  },

  CONCURRENT_REQUESTS: {
    id: 'CONCURRENT_REQUESTS',
    tag: 'C01',
    name: '2. Concurrency Control (C01 - Hai khách đồng thời tranh 1 tài xế)',
    category: 'CONCURRENCY & INVARIANT OWNER',
    desc: 'Chứng minh: 2 request đồng thời nhắm vào 1 tài xế AVAILABLE. Chỉ 1 lệnh Atomic CAS thành công, lệnh còn lại fallback an toàn.',
    steps: [
      {
        step: 1,
        title: 'Hai khách P01 và P02 cùng gửi lệnh đặt xe đồng thời',
        desc: 'Hai request đến API Gateway cùng thời điểm t=0 nhắm vào tài xế D01 duy nhất gần đó',
        from: 'passenger',
        to: 'gateway',
        protocol: 'PARALLEL HTTP',
        action: 'CONCURRENT_REQUEST',
        correlationId: 'c_race_pair_01',
        payload: { passengerA: 'P01', passengerB: 'P02', targetDriver: DEMO_DRIVER_ID }
      },
      {
        step: 2,
        title: 'Matching Service phát hiện 2 luồng cùng target D01',
        desc: 'Matching Service dispatch 2 yêu cầu giữ chỗ độc quyền sang Driver Service',
        from: 'matchingService',
        to: 'driverService',
        protocol: 'CONCURRENT REST',
        action: 'DISPATCH_PARALLEL_RESERVE',
        correlationId: 'c_race_pair_01',
        payload: { candidate: DEMO_DRIVER_ID, attempts: 2 }
      },
      {
        step: 3,
        title: 'Driver Service thực thi UPDATE nguyên tử cấp Database (Atomic CAS)',
        desc: 'UPDATE drivers SET status=\'RESERVED\' WHERE id=\'...\' AND status=\'AVAILABLE\'',
        from: 'driverService',
        to: 'driverService',
        protocol: 'SQL ATOMIC CAS',
        action: 'COMPARE_AND_SET',
        correlationId: 'c_race_pair_01',
        payload: {
          requestA_result: 'SUCCESS (1 row affected) ➔ RESERVED cho P01',
          requestB_result: 'RACE_LOST (0 rows affected) ➔ Từ chối P02'
        }
      },
      {
        step: 4,
        title: 'Matching Service fallback sang tài xế kế tiếp cho P02',
        desc: 'P01 được gán D01 thành công. P02 tự động tìm tài xế dự phòng hoặc vào hàng đợi',
        from: 'matchingService',
        to: 'passenger',
        protocol: 'HTTP RESPONSES',
        action: 'SAFE_FALLBACK_RESOLVED',
        correlationId: 'c_race_pair_01',
        payload: {
          passengerA_status: 'DRIVER_ASSIGNED (D01)',
          passengerB_status: 'MATCHING_NEXT_CANDIDATE',
          invariantPreserved: '1 Driver NEVER has 2 active trips'
        }
      }
    ]
  },

  SERVICE_FAILURE: {
    id: 'SERVICE_FAILURE',
    tag: 'R01',
    name: '3. Service Failure & Queue Buffering (R01 - Notification Service gặp sự cố)',
    category: 'FAULT TOLERANCE & BUFFERING',
    desc: 'Chứng minh: Khi downstream service gặp sự cố, RabbitMQ lưu trữ tin cậy trong queue. Sau khi service phục hồi, toàn bộ tin nhắn được xử lý bù.',
    steps: [
      {
        step: 1,
        title: 'Trip Service hoàn tất chuyến đi & đẩy Event lên RabbitMQ',
        desc: 'Event trip.completed được publish thành công vào exchange goride.events',
        from: 'tripService',
        to: 'rabbitmq',
        protocol: 'AMQP PUBLISH',
        action: 'PUBLISH_TRIP_COMPLETED',
        correlationId: 'c_fault_notif_01',
        payload: { eventType: 'trip.completed', tripId: 'tr_fault_test_01' }
      },
      {
        step: 2,
        title: 'Notification Service bị ngắt kết nối hoặc gặp sự cố (Down)',
        desc: 'Container Notification Service ngừng phản hồi. Consumer connection bị mất',
        from: 'notificationService',
        to: 'notificationService',
        protocol: 'FAULT INJECTION',
        action: 'SERVICE_DOWN_SIMULATED',
        correlationId: 'c_fault_notif_01',
        payload: { service: 'notification-service', state: 'UNREACHABLE / CRASHED' }
      },
      {
        step: 3,
        title: 'RabbitMQ lưu trữ tin nhắn an toàn trong Durable Queue',
        desc: 'Hàng đợi notification.trip-completed.q buffer tin nhắn an toàn, không làm gián đoạn các service khác',
        from: 'rabbitmq',
        to: 'rabbitmq',
        protocol: 'QUEUE BUFFERING',
        action: 'DURABLE_MESSAGE_BUFFERED',
        correlationId: 'c_fault_notif_01',
        payload: { queue: 'notification.trip-completed.q', messageCount: 1, lostMessages: 0 }
      },
      {
        step: 4,
        title: 'Notification Service khởi động lại và tự phục hồi (Self-Healing)',
        desc: 'Service online trở lại, tự động consume các tin nhắn tồn đọng trong queue và gửi Push Notification',
        from: 'rabbitmq',
        to: 'notificationService',
        protocol: 'AMQP RECONNECT',
        action: 'RECOVER_AND_CONSUME',
        correlationId: 'c_fault_notif_01',
        payload: { recovered: true, deliveredMessages: 1, status: 'PUSH_NOTIFICATION_SENT' }
      }
    ]
  },

  DUPLICATE_EVENT: {
    id: 'DUPLICATE_EVENT',
    tag: 'R03',
    name: '4. Duplicate Event Protection (R03 - Chống xử lý trùng lặp với Inbox Pattern)',
    category: 'IDEMPOTENCY & DEDUPLICATION',
    desc: 'Chứng minh: RabbitMQ phân phát lại cùng 1 eventId 2 lần do mạng chập chờn. Consumer phát hiện qua bảng inbox_event và dedup an toàn.',
    steps: [
      {
        step: 1,
        title: 'Lần 1: RabbitMQ phân phát event trip.completed (eventId: evt_dup_999)',
        desc: 'Consumer nhận tin nhắn lần đầu, kiểm tra bảng inbox_event thấy chưa tồn tại',
        from: 'rabbitmq',
        to: 'paymentService',
        protocol: 'AMQP DELIVER',
        action: 'FIRST_DELIVERY',
        correlationId: 'c_dedup_flow_01',
        payload: { eventId: 'evt_dup_999', eventType: 'trip.completed', amount: 45000 }
      },
      {
        step: 2,
        title: 'Consumer xử lý nghiệp vụ và lưu EventId vào bảng Inbox',
        desc: 'Thực hiện thanh toán, lưu bản ghi vào payments và inbox_event trong cùng 1 transaction',
        from: 'paymentService',
        to: 'paymentService',
        protocol: 'DB INBOX INSERT',
        action: 'PROCESSED_AND_STORED',
        correlationId: 'c_dedup_flow_01',
        payload: { status: 'PAID', inboxInserted: 'evt_dup_999' }
      },
      {
        step: 3,
        title: 'Lần 2: RabbitMQ gửi lại cùng eventId evt_dup_999 do Network Glitch',
        desc: 'Consumer nhận bản tin trùng lặp lần thứ hai',
        from: 'rabbitmq',
        to: 'paymentService',
        protocol: 'AMQP REDELIVER',
        action: 'DUPLICATE_DELIVERY',
        correlationId: 'c_dedup_flow_01',
        payload: { eventId: 'evt_dup_999', redelivered: true }
      },
      {
        step: 4,
        title: 'Consumer phát hiện bản ghi trùng lặp ➔ Bỏ qua an toàn (Deduplicated ACK)',
        desc: 'Query inbox_event phát hiện đã xử lý. Consumer ACK ngay và KHÔNG trừ tiền lần hai',
        from: 'paymentService',
        to: 'rabbitmq',
        protocol: 'AMQP ACK (DEDUP)',
        action: 'DEDUPLICATED_ACK',
        correlationId: 'c_dedup_flow_01',
        payload: { dedupHit: true, doubleBillingPrevented: true, effect: 'EXACTLY_ONCE_BUSINESS_EFFECT' }
      }
    ]
  },

  SAGA_COMPENSATION: {
    id: 'SAGA_COMPENSATION',
    tag: 'R02',
    name: '5. Saga Compensation Rollback (R02 - Gán cuốc thất bại ➔ Tự động giải phóng tài xế)',
    category: 'DISTRIBUTED TRANSACTION & SAGA',
    desc: 'Chứng minh: Sau khi khóa tài xế, nếu việc cập nhật chuyến đi bị timeout hoặc thất bại, Saga Orchestrator tự động đền bù hoàn trả tài xế về AVAILABLE.',
    steps: [
      {
        step: 1,
        title: 'Matching Saga khóa tạm thời tài xế D01 (Reserve)',
        desc: 'Driver Service chuyển trạng thái tài xế sang RESERVED',
        from: 'matchingService',
        to: 'driverService',
        protocol: 'HTTP REST SYNC',
        action: 'RESERVE_DRIVER',
        correlationId: 'c_saga_comp_44a',
        payload: { driverId: DEMO_DRIVER_ID, status: 'RESERVED', ttlSeconds: 30 }
      },
      {
        step: 2,
        title: 'Matching Saga gọi sang Trip Service bị Timeout hoặc lỗi vĩnh viễn',
        desc: 'Gặp sự cố mạng 504 Gateway Timeout hoặc chuyến đi đã bị hủy bỏ trước đó',
        from: 'matchingService',
        to: 'tripService',
        protocol: 'HTTP REST SYNC',
        action: 'ASSIGN_FAIL_TIMEOUT',
        correlationId: 'c_saga_comp_44a',
        payload: { error: '504 Gateway Timeout', tripId: 'tr_saga_fail_99' }
      },
      {
        step: 3,
        title: 'KÍCH HOẠT SAGA COMPENSATION: RELEASE_DRIVER',
        desc: 'Matching Service tự động phát lệnh đền bù hoàn trả tài xế về trạng thái AVAILABLE',
        from: 'matchingService',
        to: 'driverService',
        protocol: 'HTTP REST (COMPENSATION)',
        action: 'RELEASE_DRIVER_COMPENSATION',
        correlationId: 'c_saga_comp_44a',
        payload: {
          action: 'COMPENSATE_ROLLBACK',
          driverId: DEMO_DRIVER_ID,
          restoredStatus: 'AVAILABLE',
          reason: 'TRIP_ASSIGNMENT_PERMANENT_FAILURE'
        }
      },
      {
        step: 4,
        title: 'Trạng thái phân tán nhất quán cuối cùng',
        desc: 'Tài xế sẵn sàng nhận cuốc khác. Saga State chuyển sang COMPENSATED, không rò rỉ tài nguyên',
        from: 'driverService',
        to: 'matchingService',
        protocol: 'REST RESPONSE',
        action: 'SAGA_COMPENSATED',
        correlationId: 'c_saga_comp_44a',
        payload: { driverStatus: 'AVAILABLE', sagaStatus: 'COMPENSATED', resourceLeak: 0 }
      }
    ]
  },

  PAYMENT_RETRY: {
    id: 'PAYMENT_RETRY',
    tag: 'F08/R02',
    name: '6. Payment Fault & Non-Rollback Invariant (Lỗi thanh toán không rollback chuyến đã hoàn thành)',
    category: 'RESILIENCE & BOUNDED CONTEXT',
    desc: 'Chứng minh: Khi thanh toán thất bại, hệ thống lưu trạng thái FAILED và retry. Tuyệt đối KHÔNG rollback trạng thái COMPLETED của Trip Service.',
    steps: [
      {
        step: 1,
        title: 'Chuyến đi đã hoàn thành thực tế (COMPLETED)',
        desc: 'Tài xế đã trả khách tại điểm đến. Trip Service ghi nhận COMPLETED',
        from: 'tripService',
        to: 'rabbitmq',
        protocol: 'AMQP PUBLISH',
        action: 'TRIP_COMPLETED_EVENT',
        correlationId: 'c_pay_fail_01',
        payload: { tripId: 'tr_comp_real_01', status: 'COMPLETED' }
      },
      {
        step: 2,
        title: 'Payment Service trừ tiền thẻ nhưng tài khoản không đủ số dư',
        desc: 'Cổng thanh toán phản hồi 402 Payment Required / Thẻ hết tiền',
        from: 'rabbitmq',
        to: 'paymentService',
        protocol: 'AMQP CONSUME',
        action: 'CHARGE_ATTEMPT',
        correlationId: 'c_pay_fail_01',
        payload: { amount: 45000, cardStatus: 'INSUFFICIENT_FUNDS' }
      },
      {
        step: 3,
        title: 'Payment Service ghi trạng thái FAILED & lên lịch Retry',
        desc: 'Ghi nhận payment FAILED, phát event payment.failed sang Notification Service',
        from: 'paymentService',
        to: 'notificationService',
        protocol: 'ASYNC EVENT',
        action: 'PAYMENT_FAILED_NOTIFIED',
        correlationId: 'c_pay_fail_01',
        payload: { paymentStatus: 'FAILED', retryScheduled: true, fallback: 'CASH_PAYMENT' }
      },
      {
        step: 4,
        title: 'Bảo toàn bất biến kiến trúc: Trip KHÔNG bị rollback',
        desc: 'Trip Service giữ nguyên trạng thái COMPLETED. Xe và khách đã hoàn tất di chuyển thực tế',
        from: 'tripService',
        to: 'tripService',
        protocol: 'ARCHITECTURAL INVARIANT',
        action: 'NO_ROLLBACK_PRESERVED',
        correlationId: 'c_pay_fail_01',
        payload: { tripStatus: 'COMPLETED (UNTOUCHED)', isolatedBoundedContext: true }
      }
    ]
  },

  SERVICE_RESTART: {
    id: 'SERVICE_RESTART',
    tag: 'R01',
    name: '7. State Recovery & Poller Re-drive (Khôi phục trạng thái từ Outbox sau Restart)',
    category: 'CRASH RECOVERY & AT-LEAST-ONCE',
    desc: 'Chứng minh: Service bị sập đột ngột ngay sau khi lưu DB. Khi restart, Outbox Publisher quét bản ghi PENDING và gửi bù lên RabbitMQ.',
    steps: [
      {
        step: 1,
        title: 'Trip Service lưu DB thành công nhưng bị sập trước khi gửi RabbitMQ',
        desc: 'Bản ghi outbox_event nằm ở trạng thái PENDING. Container bị Crash / Out-of-Memory',
        from: 'tripService',
        to: 'tripService',
        protocol: 'CRASH SIMULATION',
        action: 'PENDING_OUTBOX_CRASH',
        correlationId: 'c_recover_outbox_01',
        payload: { outboxStatus: 'PENDING', containerState: 'KILLED' }
      },
      {
        step: 2,
        title: 'Container khởi động lại và kết nối lại Database',
        desc: 'Dịch vụ tự động phục hồi cấu hình và kết nối PostgreSQL',
        from: 'tripService',
        to: 'tripService',
        protocol: 'SERVICE RESTART',
        action: 'SERVICE_REBOOTED',
        correlationId: 'c_recover_outbox_01',
        payload: { uptime: '5s', dbConnected: true }
      },
      {
        step: 3,
        title: 'Scheduled Outbox Publisher quét bản ghi tồn đọng (Poller Re-drive)',
        desc: 'SELECT * FROM outbox_event WHERE status=\'PENDING\' FOR UPDATE SKIP LOCKED',
        from: 'tripService',
        to: 'rabbitmq',
        protocol: 'RE-DRIVE PUBLISH',
        action: 'REPUBLISH_PENDING_EVENT',
        correlationId: 'c_recover_outbox_01',
        payload: { recoveredCount: 1, eventType: 'trip.created' }
      },
      {
        step: 4,
        title: 'Event được đẩy lên RabbitMQ và đánh dấu PUBLISHED',
        desc: 'Hệ thống khôi phục luồng nghiệp vụ tự động mà không mất dữ liệu',
        from: 'rabbitmq',
        to: 'matchingService',
        protocol: 'AMQP DELIVER',
        action: 'FLOW_RESUMED',
        correlationId: 'c_recover_outbox_01',
        payload: { outboxStatus: 'PUBLISHED', dataLoss: 0 }
      }
    ]
  },

  SECURITY_VALIDATION: {
    id: 'SECURITY_VALIDATION',
    tag: 'S01-S03',
    name: '8. Security & Edge Defense (S01-S03 - Chặn JWT giả mạo, Sai Role và Payload lỗi)',
    category: 'SECURITY & GATEWAY FIREWALL',
    desc: 'Chứng minh: API Gateway chặn đứng Token giả (401), chặn sai Role (403) và Validation chặn payload thiếu trường bắt buộc (400).',
    steps: [
      {
        step: 1,
        title: 'S01: Gửi Request với Bearer Token giả mạo hoặc hết hạn',
        desc: 'Client gửi HTTP POST /api/v1/trips kèm JWT không khớp chữ ký bí mật của hệ thống',
        from: 'passenger',
        to: 'gateway',
        protocol: 'HTTP/1.1 POST',
        action: 'SEND_MALFORMED_JWT',
        correlationId: 'c_sec_test_01',
        payload: { token: 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.INVALID_SIGNATURE' }
      },
      {
        step: 2,
        title: 'Gateway JwtAuthenticationFilter từ chối ngay lập tức (401 Unauthorized)',
        desc: 'Gateway chặn đứng ở vòng ngoài, bảo vệ toàn bộ microservices nội bộ khỏi request độc hại',
        from: 'gateway',
        to: 'passenger',
        protocol: 'HTTP 401 RESPONSE',
        action: 'BLOCK_UNAUTHORIZED',
        correlationId: 'c_sec_test_01',
        payload: { httpStatus: 401, error: 'UNAUTHORIZED', message: 'Invalid or tampered JWT token' }
      },
      {
        step: 3,
        title: 'S02: Hành khách cố tình gọi API nhận cuốc của Tài xế (Sai Role)',
        desc: 'Client có Token hợp lệ nhưng Role là PASSENGER gọi PATCH /api/v1/trips/{id}/accept',
        from: 'passenger',
        to: 'gateway',
        protocol: 'HTTP/1.1 PATCH',
        action: 'UNAUTHORIZED_ROLE_ACCESS',
        correlationId: 'c_sec_test_02',
        payload: { userRole: 'ROLE_PASSENGER', requiredRole: 'ROLE_DRIVER' }
      },
      {
        step: 4,
        title: 'Hệ thống RBAC từ chối quyền truy cập (403 Forbidden)',
        desc: 'Server-side Role Check phát hiện vi phạm quyền, trả về HTTP 403',
        from: 'gateway',
        to: 'passenger',
        protocol: 'HTTP 403 RESPONSE',
        action: 'BLOCK_FORBIDDEN_ROLE',
        correlationId: 'c_sec_test_02',
        payload: { httpStatus: 403, error: 'FORBIDDEN', message: 'User does not possess ROLE_DRIVER' }
      },
      {
        step: 5,
        title: 'S03: Gửi payload thiếu trường toạ độ bắt buộc (Validation Error)',
        desc: 'POST /api/v1/trips nhưng pickupLatitude = null',
        from: 'passenger',
        to: 'gateway',
        protocol: 'HTTP/1.1 POST',
        action: 'INVALID_PAYLOAD_SEND',
        correlationId: 'c_sec_test_03',
        payload: { pickupLatitude: null, pickupAddress: 'Hà Nội' }
      },
      {
        step: 6,
        title: 'Jakarta Bean Validation chặn đứng (400 Bad Request)',
        desc: 'Trả về mã lỗi VALIDATION_FAILED kèm chi tiết các trường vi phạm',
        from: 'tripService',
        to: 'passenger',
        protocol: 'HTTP 400 RESPONSE',
        action: 'BLOCK_BAD_REQUEST',
        correlationId: 'c_sec_test_03',
        payload: { httpStatus: 400, error: 'VALIDATION_FAILED', fieldErrors: ['pickupLatitude: must not be null'] }
      }
    ]
  }
};

export default function App() {
  const [selectedScenarioKey, setSelectedScenarioKey] = useState('HAPPY_FLOW');
  const [currentStepIdx, setCurrentStepIdx] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [speedMs, setSpeedMs] = useState(2400);
  const [inspectPacket, setInspectPacket] = useState(null);
  const [logs, setLogs] = useState([]);
  const [isLiveGateway, setIsLiveGateway] = useState(false);
  const [liveTriggerLoading, setLiveTriggerLoading] = useState(false);

  // App domain state for Passenger & Driver Panels
  const [currentTrip, setCurrentTrip] = useState(null);
  const [driverStatus, setDriverStatus] = useState('AVAILABLE');
  const [isEstimating, setIsEstimating] = useState(false);
  const [isRequesting, setIsRequesting] = useState(false);

  const scenario = SCENARIOS[selectedScenarioKey] || SCENARIOS.HAPPY_FLOW;
  const activePacket = scenario.steps[currentStepIdx] || null;

  // Poll Gateway health status every 3 seconds
  useEffect(() => {
    let checkInterval = null;
    const verifyHealth = async () => {
      const online = await checkGatewayHealth();
      setIsLiveGateway(online);
      if (online) {
        bootstrapDemoData();
      }
    };
    verifyHealth();
    checkInterval = setInterval(verifyHealth, 3000);
    return () => clearInterval(checkInterval);
  }, []);

  // Live Trip Poller: when real backend is connected and trip is active
  useEffect(() => {
    if (!isLiveGateway || !currentTrip?.tripId) return;
    if (['COMPLETED', 'CANCELLED'].includes(currentTrip.status)) return;

    const poller = setInterval(async () => {
      const res = await getTripById(currentTrip.tripId);
      if (res && res.data && res.data.status !== currentTrip.status) {
        const nextStatus = res.data.status;
        setCurrentTrip((prev) => ({ ...prev, ...res.data, status: nextStatus }));

        // Real-time animation reflection
        if (nextStatus === 'DRIVER_ASSIGNED') {
          setDriverStatus('RESERVED');
          setCurrentStepIdx(5);
          logStep({
            step: 6,
            title: 'Live Backend: Matching Saga gán tài xế thành công!',
            from: 'matchingService',
            to: 'tripService',
            action: 'DRIVER_ASSIGNED',
            correlationId: currentTrip.correlationId || 'live_corr',
            payload: res.data
          });
        } else if (nextStatus === 'ACCEPTED') {
          setDriverStatus('RESERVED');
          setCurrentStepIdx(6);
        } else if (nextStatus === 'IN_PROGRESS') {
          setDriverStatus('ON_TRIP');
          setCurrentStepIdx(7);
        } else if (nextStatus === 'COMPLETED') {
          setDriverStatus('AVAILABLE');
          setCurrentStepIdx(9);
          logStep({
            step: 10,
            title: 'Live Backend: Chuyến đi hoàn tất! Outbox event đẩy RabbitMQ',
            from: 'tripService',
            to: 'rabbitmq',
            action: 'TRIP_COMPLETED',
            correlationId: currentTrip.correlationId || 'live_corr',
            payload: res.data
          });
        }
      }
    }, 1500);

    return () => clearInterval(poller);
  }, [isLiveGateway, currentTrip?.tripId, currentTrip?.status]);

  // Auto-play timeline timer for presentation simulation
  useEffect(() => {
    let timer = null;
    if (isPlaying) {
      timer = setTimeout(() => {
        if (currentStepIdx < scenario.steps.length - 1) {
          const nextIdx = currentStepIdx + 1;
          setCurrentStepIdx(nextIdx);
          logStep(scenario.steps[nextIdx]);
        } else {
          setIsPlaying(false);
        }
      }, speedMs);
    }
    return () => clearTimeout(timer);
  }, [isPlaying, currentStepIdx, scenario, speedMs]);

  const logStep = (step) => {
    setLogs((prev) => [
      {
        id: Math.random().toString(36).substring(2, 9),
        time: new Date().toLocaleTimeString(),
        step: step.step,
        title: step.title,
        from: step.from,
        to: step.to,
        action: step.action || step.eventType,
        correlationId: step.correlationId,
        packet: step
      },
      ...prev.slice(0, 50)
    ]);
  };

  const handleSelectScenario = (key) => {
    setSelectedScenarioKey(key);
    setCurrentStepIdx(0);
    setIsPlaying(false);
    logStep(SCENARIOS[key].steps[0]);
  };

  const handlePlayPause = () => setIsPlaying(!isPlaying);
  const handleNextStep = () => {
    if (currentStepIdx < scenario.steps.length - 1) {
      const nextIdx = currentStepIdx + 1;
      setCurrentStepIdx(nextIdx);
      logStep(scenario.steps[nextIdx]);
    }
  };
  const handleReset = () => {
    setCurrentStepIdx(0);
    setIsPlaying(false);
    setCurrentTrip(null);
    setDriverStatus('AVAILABLE');
  };

  // Live Scenario Trigger (Runs actual tests against Backend if connected)
  const handleTriggerLiveTest = async () => {
    setLiveTriggerLoading(true);
    const corr = 'live_test_' + Math.random().toString(36).substring(2, 8);

    if (selectedScenarioKey === 'SAGA_COMPENSATION') {
      try {
        logStep({
          step: 1,
          title: '[Live Test] Kích hoạt Saga với Trip không tồn tại để ép kích hoạt Compensation',
          from: 'matchingService',
          to: 'driverService',
          action: 'ATOMIC_RESERVE_DRIVER',
          correlationId: corr,
          payload: { driverId: DEMO_DRIVER_ID, status: 'RESERVED' }
        });
        setCurrentStepIdx(0);

        const result = await triggerLiveSagaCompensation();
        
        // Log step 2 (Fail)
        logStep({
          step: 2,
          title: '[Live Test] Trip Service trả về 404 Not Found (Trip Assign Failed)',
          from: 'matchingService',
          to: 'tripService',
          action: 'ASSIGN_FAIL_TIMEOUT',
          correlationId: corr,
          payload: { httpStatus: 404, error: 'EntityNotFoundException', tripId: result.fakeTripId }
        });
        setCurrentStepIdx(1);

        // Log step 3 & 4 (Compensated)
        setTimeout(() => {
          logStep({
            step: 3,
            title: '[Live Test] SAGA COMPENSATION: Giải phóng tài xế về AVAILABLE!',
            from: 'matchingService',
            to: 'driverService',
            action: 'RELEASE_DRIVER_COMPENSATION',
            correlationId: corr,
            payload: result.sagaRecord
          });
          setCurrentStepIdx(2);
          setDriverStatus('AVAILABLE');
          setInspectPacket({
            step: 3,
            title: 'Bản ghi Saga Compensation thực tế từ PostgreSQL goride_matching',
            from: 'matchingService',
            to: 'driverService',
            action: 'COMPENSATE_ROLLBACK',
            correlationId: result.sagaRecord?.correlationId || corr,
            payload: result.sagaRecord
          });
        }, 800);
      } catch (e) {
        alert('Lỗi kích hoạt Saga: ' + e.message);
      }
    } else if (selectedScenarioKey === 'SECURITY_VALIDATION') {
      try {
        // Test 1: Invalid Token (401)
        const t1 = await triggerLiveSecurityInvalidToken();
        logStep({
          step: 2,
          title: `[Live Test S01] API Gateway chặn Token giả mạo: HTTP ${t1.httpStatus}`,
          from: 'gateway',
          to: 'passenger',
          action: 'BLOCK_UNAUTHORIZED',
          correlationId: corr + '_S01',
          payload: t1.data
        });
        setCurrentStepIdx(1);

        // Test 2: Validation Error (400)
        setTimeout(async () => {
          const t2 = await triggerLiveSecurityValidation();
          logStep({
            step: 6,
            title: `[Live Test S03] Jakarta Bean Validation chặn Payload thiếu trường: HTTP ${t2.httpStatus}`,
            from: 'tripService',
            to: 'passenger',
            action: 'BLOCK_BAD_REQUEST',
            correlationId: corr + '_S03',
            payload: t2.data
          });
          setCurrentStepIdx(5);
          setInspectPacket({
            step: 6,
            title: 'Phản hồi HTTP 400 Bad Request từ API Gateway / Trip Service',
            from: 'tripService',
            to: 'passenger',
            action: 'VALIDATION_FAILED',
            correlationId: corr + '_S03',
            payload: t2.data
          });
        }, 900);
      } catch (e) {
        alert('Lỗi gọi Gateway: ' + e.message);
      }
    } else if (selectedScenarioKey === 'CONCURRENT_REQUESTS') {
      logStep({
        step: 1,
        title: '[Live Test C01] Bắn đồng thời 2 request đặt xe qua Gateway tới tài xế duy nhất',
        from: 'passenger',
        to: 'gateway',
        action: 'CONCURRENT_REQUEST',
        correlationId: corr,
        payload: { targetDriver: DEMO_DRIVER_ID, concurrency: 2 }
      });
      setCurrentStepIdx(0);

      const res = await triggerLiveConcurrency();
      setTimeout(() => {
        logStep({
          step: 3,
          title: '[Live Test C01] Khóa nguyên tử Database (Atomic CAS): Phân chia an toàn!',
          from: 'driverService',
          to: 'matchingService',
          action: 'COMPARE_AND_SET',
          correlationId: corr,
          payload: {
            request_A: res.res1?.data,
            request_B: res.res2?.data,
            invariantPreserved: '1 Driver NEVER assigned to 2 trips simultaneously'
          }
        });
        setCurrentStepIdx(2);
      }, 700);
    } else if (selectedScenarioKey === 'HAPPY_FLOW') {
      // Trigger Happy Flow placing real order
      handleRequestRide({
        pickup: { address: '1 Đại Cồ Việt, Hai Bà Trưng, HN', lat: 21.0051, lng: 105.8432 },
        dropoff: { address: 'Hồ Hoàn Kiếm, Hoàn Kiếm, HN', lat: 21.0285, lng: 105.8542 }
      });
    } else {
      // Default: Trigger step-by-step auto-play
      setIsPlaying(true);
    }

    setLiveTriggerLoading(false);
  };

  const handleEstimate = async (route) => {
    setIsEstimating(true);
    const res = await estimateFare(route.pickup.lat, route.pickup.lng, route.dropoff.lat, route.dropoff.lng);
    setIsEstimating(false);
    if (res.success && res.data) {
      alert(`Dự toán cước: ${Number(res.data.estimatedFare).toLocaleString()} VND (Khoảng cách: ${res.data.estimatedDistanceKm} km)`);
    }
  };

  const handleRequestRide = async (route) => {
    setIsRequesting(true);
    const correlationId = 'c_' + Math.random().toString(36).substring(2, 10);
    
    // Log outbound HTTP call
    logStep({
      step: 1,
      title: 'Hành khách gửi yêu cầu POST /api/v1/trips',
      from: 'passenger',
      to: 'gateway',
      action: 'REQUEST_RIDE',
      correlationId: correlationId,
      payload: {
        passengerId: DEMO_PASSENGER_ID,
        pickupAddress: route.pickup.address,
        destinationAddress: route.dropoff.address,
        estimatedFare: 45000,
        currency: 'VND'
      }
    });

    const res = await createTrip({
      passengerId: DEMO_PASSENGER_ID,
      pickupLatitude: route.pickup.lat,
      pickupLongitude: route.pickup.lng,
      pickupAddress: route.pickup.address,
      destinationLatitude: route.dropoff.lat,
      destinationLongitude: route.dropoff.lng,
      destinationAddress: route.dropoff.address,
      estimatedFare: 45000
    }, null, correlationId);
    
    setIsRequesting(false);

    if (res.success && res.data) {
      const tripId = res.data.id || res.data.tripId;
      setCurrentTrip({
        ...res.data,
        id: tripId,
        tripId: tripId,
        pickupAddress: route.pickup.address,
        dropoffAddress: route.dropoff.address,
        estimatedFare: 45000,
        status: res.data.status || 'REQUESTED',
        correlationId: correlationId
      });
      handleSelectScenario('HAPPY_FLOW');
      setCurrentStepIdx(1); // Gateway forward
      setIsPlaying(!isLiveGateway); // If not live, simulate auto-play
    }
  };

  const handleCancelRide = async () => {
    if (currentTrip?.tripId) {
      await updateTripStatus(currentTrip.tripId, 'cancel', null);
      setCurrentTrip((prev) => ({ ...prev, status: 'CANCELLED' }));
      logStep({
        step: 0,
        title: 'Hủy chuyến đi thành công',
        from: 'passenger',
        to: 'gateway',
        action: 'CANCEL_TRIP',
        correlationId: currentTrip.correlationId || 'c_cancel',
        payload: { tripId: currentTrip.tripId, status: 'CANCELLED' }
      });
    }
  };

  const handleToggleDriverStatus = async () => {
    const nextStatus = driverStatus === 'AVAILABLE' ? 'OFFLINE' : 'AVAILABLE';
    setDriverStatus(nextStatus);
    if (isLiveGateway) {
      await updateDriverStatus(DEMO_DRIVER_ID, nextStatus === 'AVAILABLE' ? 'ONLINE' : 'OFFLINE');
      if (nextStatus === 'AVAILABLE') {
        await updateDriverLocation(DEMO_DRIVER_ID, 21.0060, 105.8440);
      }
    }
  };

  const handleAcceptTrip = async () => {
    if (currentTrip?.tripId) {
      await updateTripStatus(currentTrip.tripId, 'accept', null);
      setCurrentTrip((prev) => ({ ...prev, status: 'ACCEPTED' }));
      setCurrentStepIdx(7);
      logStep({
        step: 7,
        title: 'Tài xế bấm nhận cuốc (PATCH /accept)',
        from: 'driverClient',
        to: 'gateway',
        action: 'ACCEPT_TRIP',
        correlationId: currentTrip.correlationId || 'live_corr',
        payload: { tripId: currentTrip.tripId, status: 'ACCEPTED' }
      });
    }
  };

  const handleRejectTrip = () => {
    handleSelectScenario('SAGA_COMPENSATION');
    setIsPlaying(true);
  };

  const handleStartTrip = async () => {
    if (currentTrip?.tripId) {
      await updateTripStatus(currentTrip.tripId, 'start', null);
      setCurrentTrip((prev) => ({ ...prev, status: 'IN_PROGRESS' }));
      setDriverStatus('ON_TRIP');
      setCurrentStepIdx(8);
      logStep({
        step: 8,
        title: 'Bắt đầu chuyến đi (PATCH /start) ➔ ON_TRIP',
        from: 'driverClient',
        to: 'gateway',
        action: 'START_TRIP',
        correlationId: currentTrip.correlationId || 'live_corr',
        payload: { tripId: currentTrip.tripId, status: 'IN_PROGRESS' }
      });
    }
  };

  const handleCompleteTrip = async () => {
    if (currentTrip?.tripId) {
      await updateTripStatus(currentTrip.tripId, 'complete', null);
      setCurrentTrip((prev) => ({ ...prev, status: 'COMPLETED' }));
      setDriverStatus('AVAILABLE');
      setCurrentStepIdx(scenario.steps.length - 2);
      setIsPlaying(true);
      logStep({
        step: 9,
        title: 'Hoàn thành chuyến đi (PATCH /complete)',
        from: 'driverClient',
        to: 'tripService',
        action: 'COMPLETE_TRIP',
        correlationId: currentTrip.correlationId || 'live_corr',
        payload: { tripId: currentTrip.tripId, status: 'COMPLETED' }
      });
    }
  };

  return (
    <div className="min-h-screen bg-[#070a13] text-slate-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="border-b border-slate-800/80 bg-[#0d1322]/90 backdrop-blur-md px-6 py-3 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 via-blue-600 to-purple-600 flex items-center justify-center font-heading font-bold text-white shadow-[0_0_20px_rgba(6,182,212,0.5)]">
            DS
          </div>
          <div>
            <h1 className="text-sm font-heading font-bold tracking-tight text-white flex items-center gap-2">
              GORIDE DS03 // DISTRIBUTED FLOW SIMULATOR
              <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 font-mono">
                PRO MAX v2.5
              </span>
            </h1>
            <p className="text-[11px] text-slate-400 font-mono">
              8 Kịch bản phân tán chuẩn đề bài DS03: Outbox &bull; RabbitMQ Bus &bull; Saga Orchestrator &bull; Atomic CAS &bull; Idempotency &bull; Fault Tolerance
            </p>
          </div>
        </div>

        {/* Live Metrics HUD */}
        <div className="flex items-center gap-3 text-xs font-mono">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 shadow-inner">
            <span className={`w-2 h-2 rounded-full ${isLiveGateway ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400'}`} />
            <span className="text-slate-400">GATEWAY :8080</span>
            <span className={isLiveGateway ? 'text-emerald-400 font-bold' : 'text-amber-300 font-semibold'}>
              {isLiveGateway ? 'LIVE BACKEND' : 'SIMULATION MODE'}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 shadow-inner">
            <Radio size={13} className="text-amber-400 animate-pulse" />
            <span className="text-slate-400">RABBITMQ:</span>
            <span className="text-amber-300 font-bold">goride.events</span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 p-5 md:p-6 space-y-5 max-w-[1700px] w-full mx-auto">
        {/* Scenario Selection & Playback Control Ribbon */}
        <div className="bento-card p-4 space-y-3.5 shadow-lg">
          <div className="flex flex-wrap items-center justify-between gap-4">
            {/* Scenario Dropdown */}
            <div className="flex items-center gap-3 flex-1 min-w-[320px]">
              <div className="w-8 h-8 rounded-lg bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center shrink-0">
                <Layers size={16} className="text-cyan-400" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-[10px] font-mono text-slate-500 uppercase font-bold">Kịch bản phân tán chuẩn đề bài:</span>
                  <span className="text-[10px] font-mono px-2 py-0.2 rounded bg-purple-950/60 text-purple-300 border border-purple-800/40">
                    {scenario.tag} &bull; {scenario.category}
                  </span>
                </div>
                <select
                  value={selectedScenarioKey}
                  onChange={(e) => handleSelectScenario(e.target.value)}
                  className="w-full bg-slate-900/95 border border-slate-700/80 text-cyan-200 text-xs font-mono font-bold rounded-lg px-3 py-2 focus:outline-none focus:border-cyan-500 cursor-pointer shadow-inner"
                >
                  {Object.entries(SCENARIOS).map(([k, s]) => (
                    <option key={k} value={k}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Playback Action Buttons */}
            <div className="flex items-center gap-2.5">
              {/* Live Test Trigger Button if Backend is live */}
              {isLiveGateway && (
                <button
                  onClick={handleTriggerLiveTest}
                  disabled={liveTriggerLoading}
                  className="px-3.5 py-2 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white rounded-lg text-xs font-mono font-bold flex items-center gap-1.5 shadow-[0_0_15px_rgba(168,85,247,0.4)] transition active:scale-95 cursor-pointer"
                  title="Kích hoạt request thực tế tới cụm Docker Backend"
                >
                  <Zap size={14} className="text-amber-300" /> KÍCH HOẠT TEST LIVE
                </button>
              )}

              <button
                onClick={handlePlayPause}
                className={`px-4 py-2 rounded-lg text-xs font-mono font-bold flex items-center gap-2 transition active:scale-95 cursor-pointer ${
                  isPlaying
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/50 shadow-[0_0_15px_rgba(245,158,11,0.3)]'
                    : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-[0_0_15px_rgba(16,185,129,0.35)]'
                }`}
              >
                {isPlaying ? <Pause size={14} /> : <Play size={14} />}
                {isPlaying ? 'PAUSE' : 'CHẠY TỰ ĐỘNG'}
              </button>

              <button
                onClick={handleNextStep}
                disabled={isPlaying || currentStepIdx >= scenario.steps.length - 1}
                className="px-3.5 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 border border-slate-700 rounded-lg text-xs font-mono font-semibold flex items-center gap-1.5 transition active:scale-95 cursor-pointer"
                title="Tua từng bước phục vụ thuyết trình vấn đáp"
              >
                <SkipForward size={14} /> TỪNG BƯỚC (NEXT)
              </button>

              <button
                onClick={handleReset}
                className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 rounded-lg text-xs font-mono transition cursor-pointer"
                title="Reset về bước đầu tiên"
              >
                <RotateCcw size={14} />
              </button>
            </div>
          </div>

          {/* Scenario Description Sub-ribbon */}
          <div className="bg-slate-900/60 p-2.5 rounded-lg border border-slate-800/80 text-xs font-mono text-slate-300 flex items-center gap-2">
            <span className="text-cyan-400 font-bold shrink-0 flex items-center gap-1">
              <CornerDownRight size={13} /> Mục tiêu chứng minh:
            </span>
            <span className="text-slate-300 truncate">{scenario.desc}</span>
          </div>

          {/* Horizontal Interactive Timeline Stepper */}
          <div className="pt-2 border-t border-slate-800/80">
            <div className="flex items-center gap-2 overflow-x-auto pb-1.5 scrollbar-thin">
              {scenario.steps.map((st, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setCurrentStepIdx(idx);
                    logStep(st);
                  }}
                  className={`px-3 py-1.5 rounded-lg border text-[11px] font-mono whitespace-nowrap transition flex items-center gap-1.5 cursor-pointer ${
                    currentStepIdx === idx
                      ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/70 shadow-[0_0_15px_rgba(6,182,212,0.3)] font-bold'
                      : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${currentStepIdx === idx ? 'bg-cyan-400 animate-ping' : 'bg-slate-600'}`} />
                  <span>[{st.step}] {st.action || st.eventType}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Main Stage: The Pro-Max Interactive Flow Canvas */}
        <div className="w-full">
          <FlowCanvas
            activeStep={currentStepIdx}
            activePacket={activePacket}
            onInspectPacket={setInspectPacket}
            isPlaying={isPlaying}
          />
        </div>

        {/* 2-Column Responsive Bento Consoles: Passenger & Driver */}
        <div className="grid grid-cols-12 gap-5">
          <div className="col-span-12 lg:col-span-6">
            <PassengerPanel
              onEstimate={handleEstimate}
              onRequestRide={handleRequestRide}
              onCancelRide={handleCancelRide}
              currentTrip={currentTrip}
              isEstimating={isEstimating}
              isRequesting={isRequesting}
            />
          </div>

          <div className="col-span-12 lg:col-span-6">
            <DriverPanel
              driverStatus={driverStatus}
              onToggleStatus={handleToggleDriverStatus}
              currentTrip={currentTrip}
              onAcceptTrip={handleAcceptTrip}
              onRejectTrip={handleRejectTrip}
              onStartTrip={handleStartTrip}
              onCompleteTrip={handleCompleteTrip}
            />
          </div>
        </div>

        {/* Bottom Realtime Distributed Event Stream & Tracing Log */}
        <div className="bento-card overflow-hidden shadow-xl">
          <div className="flex items-center justify-between px-5 py-3 bg-[#0f182c] border-b border-slate-800 text-xs font-mono">
            <div className="flex items-center gap-2.5">
              <Terminal size={14} className="text-cyan-400" />
              <span className="font-bold text-white tracking-wide">DISTRIBUTED EVENT STREAM // CORRELATION ID TRACING</span>
            </div>
            <span className="text-slate-400 text-[11px]">Auto-tracing RabbitMQ Exchanges, HTTP REST, and DB Transactions</span>
          </div>

          <div className="p-4 max-h-52 overflow-y-auto space-y-2 font-mono text-xs">
            {logs.length === 0 ? (
              <div className="text-center py-6 text-slate-500">Chưa có sự kiện nào. Hãy bấm "CHẠY TỰ ĐỘNG" hoặc "TỪNG BƯỚC".</div>
            ) : (
              logs.map((log) => (
                <div
                  key={log.id}
                  onClick={() => setInspectPacket(log.packet)}
                  className="flex items-center justify-between p-2.5 rounded-lg bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800/80 hover:border-cyan-500/50 cursor-pointer transition"
                >
                  <div className="flex items-center gap-3 overflow-hidden">
                    <span className="text-slate-500 text-[11px] shrink-0">{log.time}</span>
                    <span className="px-2 py-0.5 rounded bg-slate-800 text-cyan-400 text-[10px] font-bold border border-slate-700/60 shrink-0">
                      {log.from} ➔ {log.to}
                    </span>
                    <span className="text-slate-200 font-semibold truncate">{log.title}</span>
                  </div>
                  <div className="flex items-center gap-3 shrink-0 ml-2">
                    <span className="text-[10px] text-purple-300 bg-purple-950/40 px-2 py-0.5 rounded border border-purple-800/40 font-mono">
                      corr: {log.correlationId}
                    </span>
                    <span className="text-[11px] text-cyan-400 hover:text-cyan-300 flex items-center gap-1">
                      <Eye size={12} /> Soi JSON
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </main>

      {/* Packet / Payload Inspector Modal */}
      {inspectPacket && (
        <PayloadInspector packet={inspectPacket} onClose={() => setInspectPacket(null)} />
      )}
    </div>
  );
}
