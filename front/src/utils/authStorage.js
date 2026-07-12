const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function getAuthToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token) {
  localStorage.removeItem(TOKEN_KEY)
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

export function setStoredUser(user) {
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export function clearAuth() {
  sessionStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
