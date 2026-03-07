<template>
  <div class="page-container py-10 max-w-2xl">
    <h1 class="section-title mb-6">내 프로필</h1>

    <div v-if="loading" class="card p-8 animate-pulse space-y-4">
      <div class="skeleton h-4 rounded w-1/3"></div>
      <div class="skeleton h-4 rounded w-1/2"></div>
      <div class="skeleton h-4 rounded w-1/4"></div>
    </div>

    <div v-else-if="profile" class="card p-8">
      <!-- 아바타 -->
      <div class="flex items-center gap-5 mb-8">
        <div class="w-16 h-16 bg-yju rounded-full flex items-center justify-center text-white text-2xl font-black">
          {{ profile.username?.[0]?.toUpperCase() || 'U' }}
        </div>
        <div>
          <p class="text-lg font-bold text-gray-900">{{ profile.fullName || profile.username }}</p>
          <p class="text-sm text-gray-500">{{ profile.department || '학과 미입력' }}</p>
          <div class="flex gap-1.5 mt-1">
            <span v-for="role in profile.roles" :key="role"
              class="text-xs px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full font-medium">
              {{ role.replace('ROLE_', '') }}
            </span>
          </div>
        </div>
      </div>

      <dl class="space-y-4 text-sm divide-y divide-gray-100">
        <div class="flex justify-between pt-4 first:pt-0">
          <dt class="text-gray-500 font-medium">아이디</dt>
          <dd class="text-gray-900">{{ profile.username }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">이메일</dt>
          <dd class="text-gray-900">{{ profile.email || '-' }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">학번</dt>
          <dd class="text-gray-900">{{ profile.studentId || '-' }}</dd>
        </div>
        <div class="flex justify-between pt-4">
          <dt class="text-gray-500 font-medium">전화번호</dt>
          <dd class="text-gray-900">{{ profile.phone || '-' }}</dd>
        </div>
      </dl>

      <div class="mt-8 pt-6 border-t border-gray-100 flex gap-3">
        <RouterLink to="/" class="btn-outline text-sm py-2 px-4">홈으로</RouterLink>
        <button @click="logout" class="text-sm text-red-500 hover:text-red-700 font-medium transition-colors">
          로그아웃
        </button>
      </div>
    </div>

    <div v-else class="card p-8 text-center text-gray-500">
      <p>프로필을 불러올 수 없습니다.</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const auth    = useAuthStore()
const router  = useRouter()
const profile = ref(null)
const loading = ref(true)

function logout() {
  auth.logout()
  router.push('/')
}

onMounted(async () => {
  try {
    const { data } = await api.getMyProfile()
    profile.value  = data
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
})
</script>
