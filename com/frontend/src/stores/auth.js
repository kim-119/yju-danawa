import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token    = ref(localStorage.getItem('token') || null)
  const username = ref(localStorage.getItem('username') || null)
  const roles    = ref(JSON.parse(localStorage.getItem('roles') || '[]'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin    = computed(() => roles.value.includes('ROLE_ADMIN'))

  async function login(user, pass) {
    const { data } = await api.login(user, pass)
    token.value    = data.token
    username.value = data.username
    roles.value    = data.roles || []
    localStorage.setItem('token',    data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('roles',    JSON.stringify(data.roles || []))
  }

  function setAuth(data) {
    token.value    = data.token
    username.value = data.username
    roles.value    = data.roles || []
    localStorage.setItem('token',    data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('roles',    JSON.stringify(data.roles || []))
  }

  function logout() {
    token.value    = null
    username.value = null
    roles.value    = []
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('roles')
  }

  return { token, username, roles, isLoggedIn, isAdmin, login, setAuth, logout }
})
