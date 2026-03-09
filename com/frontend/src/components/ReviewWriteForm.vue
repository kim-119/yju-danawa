<template>
  <div class="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
    <h3 class="font-bold text-sm text-gray-900">리뷰 작성</h3>

    <!-- 완독률 선택 -->
    <div>
      <p class="text-xs text-gray-500 mb-2">얼마나 읽으셨나요?</p>
      <div class="grid grid-cols-4 gap-1.5">
        <button
          v-for="opt in completionOptions"
          :key="opt.value"
          type="button"
          @click="completionRate = opt.value"
          :class="[
            'py-2 px-1 rounded-lg text-xs font-medium transition-colors border',
            completionRate === opt.value
              ? 'bg-yju text-white border-yju'
              : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-yju hover:text-yju'
          ]"
        >
          <span class="block font-bold">{{ opt.label }}</span>
          <span class="block text-[10px] mt-0.5 opacity-70">{{ opt.sub }}</span>
        </button>
      </div>
    </div>

    <!-- 리뷰 텍스트 -->
    <div>
      <textarea
        v-model="content"
        rows="4"
        maxlength="1000"
        placeholder="이 책에 대한 생각을 자유롭게 남겨주세요..."
        class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg resize-none
               focus:outline-none focus:ring-2 focus:ring-yju/40 focus:border-yju"
      />
      <p class="text-right text-[10px] text-gray-400">{{ content.length }}/1000</p>
    </div>

    <!-- 에러 메시지 -->
    <p v-if="error" class="text-xs text-red-500">{{ error }}</p>

    <!-- 제출 버튼 -->
    <button
      @click="submit"
      :disabled="submitting || !content.trim()"
      class="w-full py-2.5 bg-yju hover:bg-yju-light disabled:bg-gray-200
             text-white disabled:text-gray-400 rounded-lg text-sm font-semibold transition-colors"
    >
      {{ submitting ? '저장 중...' : '리뷰 등록' }}
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import api from '@/api'

const props = defineProps({
  isbn13: { type: String, required: true }
})

const emit = defineEmits(['submitted'])

const completionOptions = [
  { value: 25,  label: '초반 포기', sub: '~25%' },
  { value: 50,  label: '절반 읽음', sub: '~50%' },
  { value: 75,  label: '거의 다 읽음', sub: '~75%' },
  { value: 100, label: '완독', sub: '100%' }
]

const completionRate = ref(null)
const content = ref('')
const submitting = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!content.value.trim()) {
    error.value = '리뷰 내용을 입력해주세요.'
    return
  }
  submitting.value = true
  try {
    await api.createReview(props.isbn13, content.value.trim(), completionRate.value)
    content.value = ''
    completionRate.value = null
    emit('submitted')
  } catch (e) {
    error.value = e.response?.data?.message ?? '리뷰 등록에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>
