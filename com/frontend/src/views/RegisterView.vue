<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg">
      <div class="card p-8">
        <div class="text-center mb-8">
          <h1 class="text-2xl font-black text-yju">회원가입</h1>
          <p class="text-sm text-gray-500 mt-1">영진전문대 도서관 서비스 이용 등록</p>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-5">

          <!-- 아이디 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">아이디 <span class="text-red-500">*</span></label>
            <div class="relative">
              <input
                v-model="form.username"
                type="text"
                placeholder="4~20자 영문/숫자"
                autocomplete="username"
                @blur="checkUsername"
                required
                class="input-field pr-10 transition-colors"
                :class="borderClass(usernameStatus)"
              />
              <StatusIcon :status="usernameStatus" />
            </div>
            <FieldMsg :status="usernameStatus" :msg="usernameMsg" />
          </div>

          <!-- 비밀번호 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호 <span class="text-red-500">*</span></label>
            <div class="relative">
              <input
                v-model="form.password"
                :type="showPw ? 'text' : 'password'"
                placeholder="6자 이상"
                autocomplete="new-password"
                @blur="checkPassword"
                required
                class="input-field pr-20 transition-colors"
                :class="borderClass(passwordStatus)"
              />
              <!-- 비밀번호 보기 토글 -->
              <button
                type="button"
                @click="showPw = !showPw"
                class="absolute right-8 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 focus:outline-none"
                tabindex="-1"
              >
                <svg v-if="showPw" xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                </svg>
              </button>
              <StatusIcon :status="passwordStatus" class="right-2" />
            </div>
            <FieldMsg :status="passwordStatus" :msg="passwordMsg" />
          </div>

          <!-- 이름 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">이름 <span class="text-red-500">*</span></label>
            <input v-model="form.fullName" type="text" class="input-field" placeholder="실명 입력" required />
          </div>

          <!-- 이메일 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">이메일 <span class="text-red-500">*</span></label>
            <input v-model="form.email" type="email" class="input-field" placeholder="example@yju.ac.kr" required />
          </div>

          <!-- 학번 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">학번 <span class="text-red-500">*</span></label>
            <div class="relative">
              <input
                v-model="form.studentId"
                type="text"
                placeholder="학번 입력"
                @blur="checkStudentId"
                required
                class="input-field pr-10 transition-colors"
                :class="borderClass(studentIdStatus)"
              />
              <StatusIcon :status="studentIdStatus" />
            </div>
            <FieldMsg :status="studentIdStatus" :msg="studentIdMsg" />
          </div>

          <!-- 학과 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">학과</label>
            <input v-model="form.department" type="text" class="input-field" placeholder="학과명 입력" />
          </div>

          <!-- 전화번호 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">전화번호</label>
            <input v-model="form.phone" type="tel" class="input-field" placeholder="010-0000-0000" />
          </div>

          <!-- 서버 에러 -->
          <div v-if="errorMsg" class="flex items-start gap-2 text-sm text-red-600 bg-red-50 border border-red-200 px-3 py-2.5 rounded-lg">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
            </svg>
            {{ errorMsg }}
          </div>

          <!-- 제출 버튼: 필수 검증 통과 전까지 비활성 -->
          <button
            type="submit"
            :disabled="submitting || !canSubmit"
            class="btn-primary w-full mt-2 flex items-center justify-center gap-2
                   disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <svg v-if="submitting" class="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
            </svg>
            {{ submitting ? '가입 중...' : '회원가입' }}
          </button>

          <!-- 검증 미완료 안내 -->
          <p v-if="!canSubmit && anyChecked" class="text-xs text-center text-gray-400">
            아이디, 비밀번호, 학번 확인을 완료해 주세요.
          </p>
        </form>

        <p class="text-center text-sm text-gray-500 mt-6">
          이미 계정이 있으신가요?
          <RouterLink to="/login" class="text-yju font-semibold hover:underline">로그인</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const auth      = useAuthStore()
const router    = useRouter()
const submitting = ref(false)
const errorMsg   = ref('')
const showPw     = ref(false)

// 각 필드 상태: 'idle' | 'checking' | 'ok' | 'error'
const usernameStatus  = ref('idle')
const usernameMsg     = ref('')
const passwordStatus  = ref('idle')
const passwordMsg     = ref('')
const studentIdStatus = ref('idle')
const studentIdMsg    = ref('')

const form = ref({
  username: '', password: '', fullName: '', email: '',
  studentId: '', department: '', phone: ''
})

// 필수 검증 3개 모두 ok여야 + 이름·이메일도 입력돼야 제출 가능
const canSubmit = computed(() =>
  usernameStatus.value  === 'ok' &&
  passwordStatus.value  === 'ok' &&
  studentIdStatus.value === 'ok' &&
  form.value.fullName.trim().length > 0 &&
  form.value.email.trim().length > 0
)

// 하나라도 검증을 시도했는지 (안내 문구 표시용)
const anyChecked = computed(() =>
  usernameStatus.value  !== 'idle' ||
  passwordStatus.value  !== 'idle' ||
  studentIdStatus.value !== 'idle'
)

// 입력 테두리 색: 기본 → 초록(ok) → 빨강(error)
function borderClass(status) {
  if (status === 'ok')    return '!border-green-400 focus:!ring-green-300'
  if (status === 'error') return '!border-red-400   focus:!ring-red-300'
  return ''
}

// ─── 실시간 검증 함수 ───────────────────────────────────────

async function checkUsername() {
  const val = form.value.username.trim()
  if (!val) { usernameStatus.value = 'idle'; usernameMsg.value = ''; return }
  if (val.length < 4) {
    usernameStatus.value = 'error'
    usernameMsg.value    = '아이디는 4자 이상이어야 합니다.'
    return
  }
  if (!/^[a-zA-Z0-9]+$/.test(val)) {
    usernameStatus.value = 'error'
    usernameMsg.value    = '영문, 숫자만 사용할 수 있습니다.'
    return
  }
  usernameStatus.value = 'checking'
  try {
    const { data } = await api.validateUser(val, undefined, undefined)
    if (data.usernameAvailable) {
      usernameStatus.value = 'ok'
      usernameMsg.value    = '사용 가능한 아이디입니다.'
    } else {
      usernameStatus.value = 'error'
      usernameMsg.value    = '이미 사용 중인 아이디입니다.'
    }
  } catch {
    usernameStatus.value = 'idle'
    usernameMsg.value    = ''
  }
}

async function checkPassword() {
  const val = form.value.password
  if (!val) { passwordStatus.value = 'idle'; passwordMsg.value = ''; return }
  if (val.length < 6) {
    passwordStatus.value = 'error'
    passwordMsg.value    = '비밀번호는 6자 이상이어야 합니다.'
    return
  }
  passwordStatus.value = 'checking'
  try {
    const { data } = await api.validateUser(undefined, undefined, val)
    if (data.passwordAvailable) {
      passwordStatus.value = 'ok'
      passwordMsg.value    = '사용 가능한 비밀번호입니다.'
    } else {
      passwordStatus.value = 'error'
      passwordMsg.value    = '이미 사용 중인 비밀번호입니다. 다른 비밀번호를 사용해주세요.'
    }
  } catch {
    passwordStatus.value = 'idle'
    passwordMsg.value    = ''
  }
}

async function checkStudentId() {
  const val = form.value.studentId.trim()
  if (!val) { studentIdStatus.value = 'idle'; studentIdMsg.value = ''; return }
  studentIdStatus.value = 'checking'
  try {
    const { data } = await api.validateUser(undefined, val, undefined)
    if (data.studentIdAvailable) {
      studentIdStatus.value = 'ok'
      studentIdMsg.value    = '사용 가능한 학번입니다.'
    } else {
      studentIdStatus.value = 'error'
      studentIdMsg.value    = '이미 등록된 학번입니다.'
    }
  } catch {
    studentIdStatus.value = 'idle'
    studentIdMsg.value    = ''
  }
}

// ─── 제출 ───────────────────────────────────────────────────

async function handleRegister() {
  submitting.value = true
  errorMsg.value   = ''
  try {
    const { data } = await api.register(form.value)
    auth.setAuth(data)
    router.push('/')
  } catch (e) {
    const status = e.response?.status
    const msg    = e.response?.data?.message || ''
    if (status === 409) {
      if (msg.includes('USERNAME'))   { usernameStatus.value = 'error';  errorMsg.value = '이미 사용 중인 아이디입니다.' }
      else if (msg.includes('STUDENT_ID')) { studentIdStatus.value = 'error'; errorMsg.value = '이미 등록된 학번입니다.' }
      else if (msg.includes('PASSWORD'))   { passwordStatus.value  = 'error'; errorMsg.value = '이미 사용 중인 비밀번호입니다.' }
      else errorMsg.value = '중복된 정보가 있습니다.'
    } else {
      errorMsg.value = '회원가입 중 오류가 발생했습니다.'
    }
  } finally {
    submitting.value = false
  }
}

// ─── 인라인 컴포넌트: 상태 아이콘 ───────────────────────────

const StatusIcon = {
  props: ['status'],
  setup(props) {
    return () => {
      if (props.status === 'checking') {
        return h('span', { class: 'absolute right-3 top-1/2 -translate-y-1/2' },
          h('svg', { class: 'animate-spin w-4 h-4 text-gray-400', xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24' }, [
            h('circle', { class: 'opacity-25', cx: '12', cy: '12', r: '10', stroke: 'currentColor', 'stroke-width': '4' }),
            h('path', { class: 'opacity-75', fill: 'currentColor', d: 'M4 12a8 8 0 018-8v8z' })
          ])
        )
      }
      if (props.status === 'ok') {
        return h('span', { class: 'absolute right-3 top-1/2 -translate-y-1/2' },
          h('svg', { class: 'w-4 h-4 text-green-500', xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24', stroke: 'currentColor' },
            h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'stroke-width': '2.5', d: 'M5 13l4 4L19 7' })
          )
        )
      }
      if (props.status === 'error') {
        return h('span', { class: 'absolute right-3 top-1/2 -translate-y-1/2' },
          h('svg', { class: 'w-4 h-4 text-red-500', xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24', stroke: 'currentColor' },
            h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'stroke-width': '2.5', d: 'M6 18L18 6M6 6l12 12' })
          )
        )
      }
      return h('span')
    }
  }
}

const FieldMsg = {
  props: ['status', 'msg'],
  setup(props) {
    return () => {
      if (!props.msg) return h('span')
      return h('p', {
        class: [
          'text-xs mt-1 flex items-center gap-1',
          props.status === 'ok' ? 'text-green-600' : 'text-red-500'
        ]
      }, props.msg)
    }
  }
}
</script>
