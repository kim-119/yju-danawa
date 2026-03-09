<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md">
      <div class="card p-8">

        <!-- 헤더 -->
        <div class="text-center mb-7">
          <h1 class="text-2xl font-black text-yju">계정 찾기</h1>
          <p class="text-sm text-gray-500 mt-1">아이디 또는 비밀번호를 잊으셨나요?</p>
        </div>

        <!-- 탭 -->
        <div class="flex bg-gray-100 rounded-xl p-1 mb-7">
          <button
            @click="tab = 'id'"
            :class="[
              'flex-1 py-2 rounded-lg text-sm font-semibold transition-all',
              tab === 'id' ? 'bg-white text-yju shadow-sm' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            아이디 찾기
          </button>
          <button
            @click="tab = 'pw'"
            :class="[
              'flex-1 py-2 rounded-lg text-sm font-semibold transition-all',
              tab === 'pw' ? 'bg-white text-yju shadow-sm' : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            비밀번호 재설정
          </button>
        </div>

        <!-- ── 아이디 찾기 탭 ── -->
        <div v-if="tab === 'id'">
          <div v-if="!idResult">
            <form @submit.prevent="handleFindId" class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">이름</label>
                <input
                  v-model="idForm.fullName"
                  type="text"
                  class="input-field"
                  placeholder="가입 시 등록한 이름"
                  required
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">학번</label>
                <input
                  v-model="idForm.studentId"
                  type="text"
                  class="input-field"
                  placeholder="학번 입력"
                  required
                />
              </div>

              <div v-if="idError" class="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
                {{ idError }}
              </div>

              <button
                type="submit"
                :disabled="idLoading"
                class="btn-primary w-full flex items-center justify-center gap-2"
              >
                <svg v-if="idLoading" class="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                </svg>
                {{ idLoading ? '조회 중...' : '아이디 찾기' }}
              </button>
            </form>
          </div>

          <!-- 결과 -->
          <div v-else class="text-center">
            <div class="w-14 h-14 bg-green-50 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
            </div>
            <p class="text-sm text-gray-500 mb-2">회원님의 아이디는</p>
            <p class="text-2xl font-black text-yju tracking-widest mb-1">{{ idResult }}</p>
            <p class="text-xs text-gray-400 mb-7">일부 정보는 보안상 가려집니다</p>
            <div class="flex gap-2">
              <button
                @click="idResult = ''; idForm = { fullName: '', studentId: '' }"
                class="flex-1 py-2 rounded-lg text-sm border border-gray-200 text-gray-600 hover:bg-gray-50 transition-colors"
              >
                다시 찾기
              </button>
              <RouterLink to="/login" class="flex-1 btn-primary text-center text-sm py-2">
                로그인하기
              </RouterLink>
            </div>
          </div>
        </div>

        <!-- ── 비밀번호 재설정 탭 ── -->
        <div v-if="tab === 'pw'">
          <div v-if="!pwDone">
            <form @submit.prevent="handleResetPw" class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">아이디</label>
                <input
                  v-model="pwForm.username"
                  type="text"
                  class="input-field"
                  placeholder="가입한 아이디"
                  required
                  autocomplete="username"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">학번</label>
                <input
                  v-model="pwForm.studentId"
                  type="text"
                  class="input-field"
                  placeholder="학번 입력"
                  required
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">새 비밀번호</label>
                <div class="relative">
                  <input
                    v-model="pwForm.newPassword"
                    :type="showNewPw ? 'text' : 'password'"
                    class="input-field pr-10"
                    placeholder="6자 이상"
                    required
                    minlength="6"
                    autocomplete="new-password"
                  />
                  <button
                    type="button"
                    @click="showNewPw = !showNewPw"
                    class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    tabindex="-1"
                  >
                    <svg v-if="showNewPw" xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/>
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                    </svg>
                  </button>
                </div>
                <p v-if="pwForm.newPassword && pwForm.newPassword.length < 6" class="text-xs text-red-500 mt-1">
                  비밀번호는 6자 이상이어야 합니다.
                </p>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">새 비밀번호 확인</label>
                <input
                  v-model="pwForm.confirm"
                  :type="showNewPw ? 'text' : 'password'"
                  class="input-field"
                  :class="pwMismatch ? '!border-red-400' : ''"
                  placeholder="비밀번호 재입력"
                  required
                  autocomplete="new-password"
                />
                <p v-if="pwMismatch" class="text-xs text-red-500 mt-1">비밀번호가 일치하지 않습니다.</p>
              </div>

              <div v-if="pwError" class="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
                {{ pwError }}
              </div>

              <button
                type="submit"
                :disabled="pwLoading || pwMismatch || pwForm.newPassword.length < 6"
                class="btn-primary w-full flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <svg v-if="pwLoading" class="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                </svg>
                {{ pwLoading ? '변경 중...' : '비밀번호 변경' }}
              </button>
            </form>
          </div>

          <!-- 완료 -->
          <div v-else class="text-center">
            <div class="w-14 h-14 bg-green-50 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
            </div>
            <p class="text-base font-bold text-gray-800 mb-1">비밀번호가 변경되었습니다</p>
            <p class="text-sm text-gray-400 mb-7">새 비밀번호로 로그인해 주세요.</p>
            <RouterLink to="/login" class="btn-primary block text-center text-sm py-2.5">
              로그인하러 가기
            </RouterLink>
          </div>
        </div>

        <!-- 하단 링크 -->
        <p class="text-center text-sm text-gray-500 mt-7">
          <RouterLink to="/login" class="text-yju font-semibold hover:underline">로그인으로 돌아가기</RouterLink>
        </p>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import api from '@/api'

const tab = ref('id')

// 아이디 찾기
const idForm    = ref({ fullName: '', studentId: '' })
const idLoading = ref(false)
const idError   = ref('')
const idResult  = ref('')

async function handleFindId() {
  idLoading.value = true
  idError.value   = ''
  try {
    const { data } = await api.findUsername(idForm.value.studentId, idForm.value.fullName)
    idResult.value = data.maskedUsername
  } catch (e) {
    const status = e.response?.status
    if (status === 404) idError.value = '입력하신 정보와 일치하는 계정이 없습니다.'
    else idError.value = '조회 중 오류가 발생했습니다.'
  } finally {
    idLoading.value = false
  }
}

// 비밀번호 재설정
const pwForm     = ref({ username: '', studentId: '', newPassword: '', confirm: '' })
const pwLoading  = ref(false)
const pwError    = ref('')
const pwDone     = ref(false)
const showNewPw  = ref(false)

const pwMismatch = computed(() =>
  pwForm.value.confirm.length > 0 && pwForm.value.newPassword !== pwForm.value.confirm
)

async function handleResetPw() {
  if (pwMismatch.value) return
  pwLoading.value = true
  pwError.value   = ''
  try {
    await api.resetPassword(pwForm.value.username, pwForm.value.studentId, pwForm.value.newPassword)
    pwDone.value = true
  } catch (e) {
    const status = e.response?.status
    if (status === 404) pwError.value = '아이디 또는 학번이 올바르지 않습니다.'
    else if (status === 400) pwError.value = '비밀번호는 6자 이상이어야 합니다.'
    else pwError.value = '변경 중 오류가 발생했습니다.'
  } finally {
    pwLoading.value = false
  }
}
</script>
