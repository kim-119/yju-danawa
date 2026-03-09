<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md">
      <!-- 카드 -->
      <div class="card p-8">
        <div class="text-center mb-8">
          <h1 class="text-2xl font-black text-yju">로그인</h1>
          <p class="text-sm text-gray-500 mt-1">Y-다나와에 오신 것을 환영합니다</p>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">아이디</label>
            <input
              v-model="form.username"
              type="text"
              class="input-field"
              placeholder="아이디를 입력하세요"
              required
              autocomplete="username"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
            <div class="relative">
              <input
                v-model="form.password"
                :type="showPw ? 'text' : 'password'"
                class="input-field pr-10"
                placeholder="비밀번호를 입력하세요"
                required
                autocomplete="current-password"
              />
              <button
                type="button"
                @click="showPw = !showPw"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                <svg v-if="showPw" xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 에러 -->
          <div v-if="errorMsg" class="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
            {{ errorMsg }}
          </div>

          <button
            type="submit"
            :disabled="submitting"
            class="btn-primary w-full mt-2"
          >
            <svg v-if="submitting" class="animate-spin w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
            </svg>
            {{ submitting ? '로그인 중...' : '로그인' }}
          </button>
        </form>

        <div class="flex items-center justify-between mt-6">
          <RouterLink
            to="/account-recovery"
            class="text-xs text-gray-400 hover:text-gray-600 hover:underline transition-colors"
          >
            아이디 · 비밀번호 찾기
          </RouterLink>
          <p class="text-sm text-gray-500">
            계정이 없으신가요?
            <RouterLink to="/register" class="text-yju font-semibold hover:underline">회원가입</RouterLink>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth      = useAuthStore()
const router    = useRouter()
const route     = useRoute()
const submitting = ref(false)
const errorMsg   = ref('')
const showPw     = ref(false)

const form = ref({ username: '', password: '' })

async function handleLogin() {
  submitting.value = true
  errorMsg.value   = ''
  try {
    await auth.login(form.value.username, form.value.password)
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    const status = e.response?.status
    if (status === 401) errorMsg.value = '아이디 또는 비밀번호가 올바르지 않습니다.'
    else if (status === 403) errorMsg.value = '계정이 잠겨 있습니다. 관리자에게 문의하세요.'
    else errorMsg.value = '로그인 중 오류가 발생했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>
