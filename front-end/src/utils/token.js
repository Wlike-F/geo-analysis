const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokenPair(tokenPair) {
  if (tokenPair?.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokenPair.accessToken)
  }
  if (tokenPair?.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokenPair.refreshToken)
  }
}

export function clearTokenPair() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
