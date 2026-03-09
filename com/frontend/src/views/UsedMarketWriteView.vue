<template>
  <div class="page-container py-8 max-w-2xl">

    <!-- 헤더 -->
    <div class="flex items-center gap-3 mb-7">
      <button @click="$router.back()" class="text-gray-400 hover:text-gray-700 transition-colors">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
        </svg>
      </button>
      <h1 class="text-xl font-black text-gray-900">중고 책 등록</h1>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-5">

      <!-- 이미지 업로드 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-2">
          이미지
          <span class="text-gray-400 font-normal ml-1">(최대 5장, 선택)</span>
        </label>

        <!-- 업로드 영역 -->
        <div
          class="border-2 border-dashed border-gray-200 rounded-xl p-5 text-center
                 hover:border-yju hover:bg-blue-50/30 transition-all cursor-pointer"
          :class="isDragOver ? 'border-yju bg-blue-50/40' : ''"
          @click="fileInput.click()"
          @dragover.prevent="isDragOver = true"
          @dragleave="isDragOver = false"
          @drop.prevent="onDrop"
        >
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            class="hidden"
            @change="onFileChange"
          />
          <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 mx-auto mb-2 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
          </svg>
          <p class="text-sm text-gray-400">클릭하거나 이미지를 드래그해서 올리세요</p>
          <p class="text-xs text-gray-300 mt-1">모든 이미지 형식 지원</p>
        </div>

        <!-- 미리보기 -->
        <div v-if="previews.length > 0" class="flex gap-2 mt-3 flex-wrap">
          <div
            v-for="(preview, i) in previews"
            :key="i"
            class="relative w-20 h-20 rounded-lg overflow-hidden border border-gray-200 group"
          >
            <img :src="preview" class="w-full h-full object-cover" />
            <button
              type="button"
              @click.stop="removeImage(i)"
              class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100
                     flex items-center justify-center transition-opacity"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <p class="w-full text-xs text-gray-400 mt-1">{{ files.length }}장 선택됨 — 호버하면 삭제</p>
        </div>
      </div>

      <!-- 책 제목 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">
          책 제목 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.title"
          type="text"
          class="input-field"
          placeholder="책 제목을 입력하세요"
          required
        />
      </div>

      <!-- 저자 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">저자</label>
        <input
          v-model="form.author"
          type="text"
          class="input-field"
          placeholder="저자명"
        />
      </div>

      <!-- 가격 + 상태 (2열) -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            판매 가격 <span class="text-red-500">*</span>
          </label>
          <div class="relative">
            <input
              v-model.number="form.priceWon"
              type="number"
              min="0"
              class="input-field pr-8"
              placeholder="0"
              required
            />
            <span class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">원</span>
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">판매 상태</label>
          <select v-model="form.status" class="input-field bg-white">
            <option value="AVAILABLE">판매중</option>
            <option value="RESERVED">예약중</option>
            <option value="SOLD">판매완료</option>
          </select>
        </div>
      </div>

      <!-- 학과 카테고리 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">학과 카테고리</label>
        <select v-model="form.departmentId" class="input-field bg-white">
          <option :value="null">학과 선택 (선택사항)</option>
          <option v-for="dept in departments" :key="dept.id" :value="dept.id">
            {{ dept.name }}
          </option>
        </select>
      </div>

      <!-- ISBN -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">ISBN</label>
        <input
          v-model="form.isbn"
          type="text"
          class="input-field"
          placeholder="9791234567890 (선택사항)"
          maxlength="20"
        />
      </div>

      <!-- 설명 -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">
          상세 설명
          <span class="text-gray-400 font-normal ml-1">(책 상태, 낙서 유무 등)</span>
        </label>
        <textarea
          v-model="form.description"
          rows="5"
          class="input-field resize-none"
          placeholder="책 상태를 자세히 설명해 주세요. (낙서, 형광펜, 찢김 여부 등)"
          maxlength="1000"
        />
        <p class="text-right text-xs text-gray-400 mt-1">{{ (form.description || '').length }} / 1000</p>
      </div>

      <!-- 에러 -->
      <div v-if="errorMsg" class="flex items-start gap-2 text-sm text-red-600 bg-red-50 border border-red-200 px-3 py-2.5 rounded-lg">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
        </svg>
        {{ errorMsg }}
      </div>

      <!-- 버튼 -->
      <div class="flex gap-3 pt-2">
        <button
          type="button"
          @click="$router.back()"
          class="flex-1 py-3 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600
                 hover:bg-gray-50 transition-colors"
        >
          취소
        </button>
        <button
          type="submit"
          :disabled="submitting || !form.title || form.priceWon === null"
          class="flex-1 btn-primary py-3 flex items-center justify-center gap-2
                 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <svg v-if="submitting" class="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
          </svg>
          {{ submitting ? '등록 중...' : '등록하기' }}
        </button>
      </div>

    </form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'

const router = useRouter()

const form = ref({
  title: '',
  author: '',
  priceWon: null,
  status: 'AVAILABLE',
  departmentId: null,
  isbn: '',
  description: '',
})

const departments = ref([])
const files       = ref([])
const previews    = ref([])
const isDragOver  = ref(false)
const submitting  = ref(false)
const errorMsg    = ref('')
const fileInput   = ref(null)

onMounted(async () => {
  try {
    const { data } = await api.getDepartments()
    departments.value = data
  } catch {
    departments.value = []
  }
})

function onFileChange(e) {
  addFiles(Array.from(e.target.files))
  e.target.value = ''
}

function onDrop(e) {
  isDragOver.value = false
  addFiles(Array.from(e.dataTransfer.files).filter(f => f.type.startsWith('image/')))
}

function addFiles(newFiles) {
  const remaining = 5 - files.value.length
  if (remaining <= 0) return
  const toAdd = newFiles.slice(0, remaining)
  toAdd.forEach(f => {
    files.value.push(f)
    const reader = new FileReader()
    reader.onload = e => previews.value.push(e.target.result)
    reader.readAsDataURL(f)
  })
}

function removeImage(index) {
  files.value.splice(index, 1)
  previews.value.splice(index, 1)
}

async function handleSubmit() {
  if (!form.value.title.trim()) { errorMsg.value = '책 제목을 입력해 주세요.'; return }
  if (form.value.priceWon === null || form.value.priceWon < 0) { errorMsg.value = '가격을 입력해 주세요.'; return }

  submitting.value = true
  errorMsg.value   = ''

  const fd = new FormData()
  fd.append('title', form.value.title.trim())
  if (form.value.author)       fd.append('author', form.value.author.trim())
  fd.append('priceWon', form.value.priceWon)
  fd.append('status', form.value.status)
  if (form.value.departmentId) fd.append('departmentId', form.value.departmentId)
  if (form.value.isbn)         fd.append('isbn', form.value.isbn.trim())
  if (form.value.description)  fd.append('description', form.value.description.trim())
  files.value.forEach(f => fd.append('images', f))

  try {
    const { data } = await api.createUsedBook(fd)
    router.push({ name: 'market-detail', params: { id: data.id } })
  } catch (e) {
    const status = e.response?.status
    if (status === 401) {
      errorMsg.value = '로그인이 필요합니다.'
    } else if (status === 400) {
      errorMsg.value = e.response?.data?.message || '입력값을 확인해 주세요.'
    } else {
      errorMsg.value = '등록 중 오류가 발생했습니다. 다시 시도해 주세요.'
    }
  } finally {
    submitting.value = false
  }
}
</script>
