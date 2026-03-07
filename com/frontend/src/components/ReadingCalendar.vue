<template>
  <div>
    <!-- 로딩 -->
    <div v-if="calLoading" class="flex justify-center items-center h-64">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
    </div>

    <!-- 캘린더 본체 -->
    <div v-else class="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
      <FullCalendar :options="calendarOptions" />
    </div>

    <!-- 기록 추가 모달 -->
    <Teleport to="body">
      <div
        v-if="showAddModal"
        class="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4"
        @click.self="closeAddModal"
      >
        <div class="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 animate-in">
          <div class="flex items-center gap-2 mb-1">
            <span class="text-xl">📖</span>
            <h3 class="text-lg font-bold text-gray-900">독서 기록 추가</h3>
          </div>
          <p class="text-sm text-blue-600 font-medium mb-5 pl-8">{{ formattedSelectedDate }}</p>

          <div class="space-y-4">
            <!-- 책 제목 -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                책 제목 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.bookTitle"
                type="text"
                placeholder="읽은 책 제목을 입력하세요"
                class="input-field"
                :class="{ 'border-red-400 focus:ring-red-400/40 focus:border-red-400': formErrors.bookTitle }"
                @keyup.enter="saveLog"
              />
              <p v-if="formErrors.bookTitle" class="text-xs text-red-500 mt-1">책 제목은 필수입니다.</p>
            </div>

            <!-- 읽은 페이지 -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">읽은 페이지 수</label>
              <input
                v-model.number="form.pagesRead"
                type="number"
                placeholder="예: 50"
                min="1"
                class="input-field"
              />
            </div>

            <!-- 메모 -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">메모</label>
              <textarea
                v-model="form.memo"
                rows="3"
                placeholder="오늘의 독서 감상을 자유롭게 남겨보세요"
                class="input-field resize-none"
              ></textarea>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button @click="saveLog" :disabled="saving" class="btn-primary flex-1 py-2.5 text-sm">
              <span v-if="saving" class="inline-flex items-center justify-center gap-2">
                <span class="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></span>
                저장 중...
              </span>
              <span v-else>저장</span>
            </button>
            <button @click="closeAddModal" class="btn-outline flex-1 py-2.5 text-sm">취소</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 기록 상세/삭제 모달 -->
    <Teleport to="body">
      <div
        v-if="showDetailModal"
        class="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4"
        @click.self="closeDetailModal"
      >
        <div class="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 animate-in">
          <div class="flex items-start justify-between mb-4">
            <div>
              <div class="flex items-center gap-2">
                <span class="text-xl">📖</span>
                <h3 class="text-lg font-bold text-gray-900">{{ selectedLog.bookTitle }}</h3>
              </div>
              <p class="text-sm text-blue-600 font-medium mt-0.5 pl-8">{{ formatDate(selectedLog.logDate) }}</p>
            </div>
            <button
              @click="closeDetailModal"
              class="text-gray-400 hover:text-gray-600 transition-colors p-1 -mt-1 -mr-1"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <dl class="space-y-3 text-sm">
            <div v-if="selectedLog.pagesRead" class="flex justify-between items-center py-2 border-b border-gray-50">
              <dt class="text-gray-500 font-medium">읽은 페이지</dt>
              <dd class="font-bold text-gray-900">{{ selectedLog.pagesRead }}<span class="font-normal text-gray-500 ml-0.5">p</span></dd>
            </div>
            <div v-if="selectedLog.memo">
              <dt class="text-gray-500 font-medium mb-1.5">메모</dt>
              <dd class="bg-gray-50 rounded-xl p-3.5 text-gray-700 leading-relaxed whitespace-pre-wrap">{{ selectedLog.memo }}</dd>
            </div>
            <div v-if="!selectedLog.pagesRead && !selectedLog.memo" class="text-center py-4 text-gray-400">
              <p>남긴 메모가 없습니다.</p>
            </div>
          </dl>

          <div class="flex gap-3 mt-6">
            <button
              @click="deleteLog(selectedLog.id)"
              :disabled="deleting"
              class="flex-1 py-2.5 text-sm font-semibold rounded-lg border-2 border-red-300
                     text-red-500 hover:bg-red-500 hover:text-white hover:border-red-500
                     transition-colors disabled:opacity-50"
            >
              {{ deleting ? '삭제 중...' : '삭제' }}
            </button>
            <button @click="closeDetailModal" class="btn-outline flex-1 py-2.5 text-sm">닫기</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import interactionPlugin from '@fullcalendar/interaction'
import koLocale from '@fullcalendar/core/locales/ko'
import api from '@/api'

// ─────────────────────────────────────────────────────────────
// 개발/테스트용 더미 데이터
// 백엔드 연동이 완료되면 아래 USE_DUMMY_DATA를 false로 변경하세요.
// ─────────────────────────────────────────────────────────────
const USE_DUMMY_DATA = true

const DUMMY_LOGS = [
  {
    id: 1,
    bookTitle: '클린 코드',
    pagesRead: 52,
    memo: '1~3장 정독. 함수는 한 가지 일만 해야 한다는 원칙이 인상적이었다.',
    logDate: '2026-03-03'
  },
  {
    id: 2,
    bookTitle: '모두의 파이썬',
    pagesRead: 30,
    memo: '기초 문법 복습 완료',
    logDate: '2026-03-05'
  },
  {
    id: 3,
    bookTitle: '클린 코드',
    pagesRead: 45,
    memo: '4~5장 마무리. 주석은 코드로 표현 못할 때만 쓴다.',
    logDate: '2026-03-07'
  }
]

// ─────────────────────────────────────────────────────────────
// 상태
// ─────────────────────────────────────────────────────────────
const calLoading = ref(true)
const logs = ref([])

// 추가 모달
const showAddModal = ref(false)
const selectedDate = ref('')
const saving = ref(false)
const formErrors = ref({})
const form = ref({ bookTitle: '', pagesRead: null, memo: '' })

// 상세 모달
const showDetailModal = ref(false)
const selectedLog = ref({})
const deleting = ref(false)

// ─────────────────────────────────────────────────────────────
// FullCalendar 이벤트 변환
// ─────────────────────────────────────────────────────────────
const calendarEvents = computed(() =>
  logs.value.map(log => ({
    id: String(log.id),
    title: log.pagesRead ? `${log.bookTitle} (${log.pagesRead}p)` : log.bookTitle,
    date: log.logDate,
    extendedProps: { ...log }
  }))
)

// ─────────────────────────────────────────────────────────────
// FullCalendar 옵션
// ─────────────────────────────────────────────────────────────
const calendarOptions = computed(() => ({
  plugins: [dayGridPlugin, interactionPlugin],
  initialView: 'dayGridMonth',
  locale: koLocale,
  headerToolbar: {
    left: 'prev,next today',
    center: 'title',
    right: ''
  },
  buttonText: { today: '오늘' },
  events: calendarEvents.value,
  dateClick: handleDateClick,
  eventClick: handleEventClick,
  height: 'auto',
  fixedWeekCount: false,
  dayMaxEvents: 3,
  eventColor: '#2563eb',
  eventBorderColor: 'transparent',
  eventTextColor: '#ffffff',
  eventDisplay: 'block'
}))

// ─────────────────────────────────────────────────────────────
// 날짜 클릭 → 추가 모달
// ─────────────────────────────────────────────────────────────
function handleDateClick(info) {
  selectedDate.value = info.dateStr
  form.value = { bookTitle: '', pagesRead: null, memo: '' }
  formErrors.value = {}
  showAddModal.value = true
}

// ─────────────────────────────────────────────────────────────
// 이벤트 클릭 → 상세 모달
// ─────────────────────────────────────────────────────────────
function handleEventClick(info) {
  selectedLog.value = { ...info.event.extendedProps, id: info.event.id }
  showDetailModal.value = true
}

// ─────────────────────────────────────────────────────────────
// 날짜 포맷 헬퍼
// ─────────────────────────────────────────────────────────────
const formattedSelectedDate = computed(() => formatDate(selectedDate.value))

function formatDate(dateStr) {
  if (!dateStr) return ''
  const [y, m, d] = dateStr.split('-')
  return `${y}년 ${parseInt(m)}월 ${parseInt(d)}일`
}

// ─────────────────────────────────────────────────────────────
// 독서 기록 저장 — POST /api/reading-logs
// ─────────────────────────────────────────────────────────────
async function saveLog() {
  formErrors.value = {}
  if (!form.value.bookTitle.trim()) {
    formErrors.value.bookTitle = true
    return
  }

  saving.value = true
  try {
    const payload = {
      bookTitle: form.value.bookTitle.trim(),
      pagesRead: form.value.pagesRead || null,
      memo:      form.value.memo.trim() || null,
      logDate:   selectedDate.value
    }

    if (USE_DUMMY_DATA) {
      // 더미 모드: 로컬 상태에만 추가 (백엔드 연동 시 아래 실제 API 호출로 교체)
      logs.value.push({ id: Date.now(), ...payload })
    } else {
      // 실제 API 호출
      // Spring Boot: POST /api/reading-logs (JWT 인증 헤더 자동 첨부)
      const { data } = await api.createReadingLog(payload)
      logs.value.push(data)
    }

    closeAddModal()
  } catch (err) {
    console.error('[독서 일지] 저장 실패:', err)
  } finally {
    saving.value = false
  }
}

// ─────────────────────────────────────────────────────────────
// 독서 기록 삭제 — DELETE /api/reading-logs/:id
// ─────────────────────────────────────────────────────────────
async function deleteLog(id) {
  deleting.value = true
  try {
    if (USE_DUMMY_DATA) {
      // 더미 모드: 로컬 상태에서만 제거 (백엔드 연동 시 아래 실제 API 호출로 교체)
      logs.value = logs.value.filter(l => String(l.id) !== String(id))
    } else {
      // 실제 API 호출
      // Spring Boot: DELETE /api/reading-logs/{id}
      await api.deleteReadingLog(id)
      logs.value = logs.value.filter(l => String(l.id) !== String(id))
    }

    closeDetailModal()
  } catch (err) {
    console.error('[독서 일지] 삭제 실패:', err)
  } finally {
    deleting.value = false
  }
}

// ─────────────────────────────────────────────────────────────
// 모달 닫기
// ─────────────────────────────────────────────────────────────
function closeAddModal() { showAddModal.value = false }
function closeDetailModal() { showDetailModal.value = false }

// ─────────────────────────────────────────────────────────────
// 초기 데이터 로드 — GET /api/reading-logs
// ─────────────────────────────────────────────────────────────
onMounted(async () => {
  try {
    if (USE_DUMMY_DATA) {
      // 더미 데이터 사용 (백엔드 연동 시 아래 실제 API 호출로 교체)
      logs.value = [...DUMMY_LOGS]
    } else {
      // 실제 API 호출
      // Spring Boot: GET /api/reading-logs (현재 로그인 사용자 기록 반환)
      const { data } = await api.getReadingLogs()
      logs.value = data || []
    }
  } catch (err) {
    console.error('[독서 일지] 로드 실패:', err)
    logs.value = []
  } finally {
    calLoading.value = false
  }
})
</script>

<style scoped>
/* ─── FullCalendar 오버라이드 ─────────────────────────────── */

/* 툴바 제목 */
:deep(.fc-toolbar-title) {
  font-size: 1rem;
  font-weight: 700;
  color: #111827;
}

/* 툴바 버튼 */
:deep(.fc-button-primary) {
  background-color: #2563eb !important;
  border-color: #2563eb !important;
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: 0.5rem !important;
  padding: 0.35rem 0.8rem !important;
  box-shadow: none !important;
}
:deep(.fc-button-primary:hover) {
  background-color: #1d4ed8 !important;
  border-color: #1d4ed8 !important;
}
:deep(.fc-button-primary:not(:disabled):active),
:deep(.fc-button-primary:not(:disabled).fc-button-active) {
  background-color: #1e40af !important;
  border-color: #1e40af !important;
}
:deep(.fc-button-primary:focus) {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.3) !important;
}

/* 오늘 날짜 셀 */
:deep(.fc-day-today) {
  background-color: #eff6ff !important;
}
:deep(.fc-day-today .fc-daygrid-day-number) {
  color: #2563eb;
  font-weight: 700;
  background-color: #2563eb;
  color: white;
  border-radius: 50%;
  width: 1.6rem;
  height: 1.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
}

/* 날짜 숫자 */
:deep(.fc-daygrid-day-number) {
  font-size: 0.8rem;
  color: #374151;
  padding: 4px 6px;
}

/* 요일 헤더 */
:deep(.fc-col-header-cell) {
  font-weight: 600;
  font-size: 0.75rem;
  color: #6b7280;
  padding: 8px 0;
  background-color: #f9fafb;
}

/* 날짜 셀 hover */
:deep(.fc-daygrid-day:hover) {
  background-color: #f0f9ff;
  cursor: pointer;
}

/* 이벤트 */
:deep(.fc-event) {
  border-radius: 4px;
  font-size: 0.7rem;
  padding: 2px 5px;
  cursor: pointer;
  border: none !important;
}
:deep(.fc-event:hover) {
  opacity: 0.88;
}

/* more 링크 */
:deep(.fc-more-link) {
  font-size: 0.7rem;
  color: #6b7280;
  font-weight: 500;
}

/* 그리드 외곽선 */
:deep(.fc-scrollgrid) {
  border: none !important;
}
:deep(.fc-scrollgrid td),
:deep(.fc-scrollgrid th) {
  border-color: #f3f4f6 !important;
}

/* 툴바 패딩 */
:deep(.fc-toolbar) {
  padding: 1rem 1.25rem 0.75rem;
}

/* 모달 페이드인 */
.animate-in {
  animation: fadeInUp 0.18s ease-out;
}
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(12px) scale(0.97); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
