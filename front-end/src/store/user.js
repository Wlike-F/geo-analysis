import { defineStore } from 'pinia'
import { getUserInfo } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: {
      id: null,
      username: '',
      realName: '',
      role: '',
      email: '',
      avatar: '',
      introduction: ''
    }
  }),
  getters: {
    isAdmin(state) {
      return state.userInfo.role === 'admin'
    },
    displayName(state) {
      return state.userInfo.realName || state.userInfo.username || '未知用户'
    },
    displayAvatar(state) {
      return state.userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
    }
  },
  actions: {
    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        if (res) {
          this.userInfo = res
        }
      } catch (error) {
        console.error('获取用户信息失败', error)
      }
    },
    logout() {
      this.userInfo = {}
      localStorage.removeItem('token')
    }
  }
})
