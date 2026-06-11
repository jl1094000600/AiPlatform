<template>
  <div class="forbidden-page">
    <div class="forbidden-box">
      <h1>403</h1>
      <h2>暂无访问权限</h2>
      <p>当前账号没有访问该页面的权限，请切换租户或联系管理员调整角色权限。</p>
      <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.forbidden-page {
  min-height: 520px;
  display: grid;
  place-items: center;
}

.forbidden-box {
  text-align: center;
  color: #111827;
}

.forbidden-box h1 {
  margin: 0;
  font-size: 88px;
  line-height: 1;
  color: #2563eb;
}

.forbidden-box h2 {
  margin: 18px 0 8px;
}

.forbidden-box p {
  margin: 0 0 24px;
  color: #64748b;
}
</style>
