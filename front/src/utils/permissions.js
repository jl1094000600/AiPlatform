export function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
}

export function isPlatformAdmin(user = readStoredUser()) {
  return user?.platformAdmin === true
}

export function hasPermission(permissionCode, user = readStoredUser()) {
  if (!permissionCode) return true
  if (isPlatformAdmin(user)) return true
  return Array.isArray(user?.permissions) && user.permissions.includes(permissionCode)
}

export function filterNavByPermissions(items, user = readStoredUser()) {
  return (items || [])
    .map(item => {
      const children = filterNavByPermissions(item.children || [], user)
      const allowed = !item.permissionCode || hasPermission(item.permissionCode, user)
      if (!allowed && !children.length) return null
      return { ...item, children }
    })
    .filter(Boolean)
}

export function hasCompleteAuthState(user = readStoredUser()) {
  return Array.isArray(user?.permissions) && Array.isArray(user?.menus)
}
