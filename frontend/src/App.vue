<!-- AI-assisted application shell with Codex. -->
<script setup>
import { onMounted, onBeforeUnmount, nextTick } from "vue";
import HomeView from "./HomeView.vue";
import CartView from "./views/CartView.vue";
import OrdersView from "./views/OrdersView.vue";
import AdminView from "./views/AdminView.vue";
import AuthDialog from "./components/AuthDialog.vue";
import { shop, initialize, notify, navigate } from "./lib/shop";
import { request, session } from "./lib/api";
import { routeFromHash } from "./lib/route.js";
function routeChanged() {
  const hash = window.location.hash.slice(1);
  shop.route = routeFromHash(hash);
  if (hash.startsWith("/") && shop.route !== "/")
    nextTick(() => window.scrollTo({ top: 0 }));
}
async function logout() {
  try {
    await request("/auth/logout", { method: "POST" });
    shop.user = null;
    shop.cartQuantity = 0;
    shop.checkoutDraft = null;
    await session();
    navigate("/");
    notify("已安全退出，下次再来逛逛。");
  } catch (e) {
    notify(e.message, "error");
  }
}
onMounted(() => {
  routeChanged();
  window.addEventListener("hashchange", routeChanged);
  initialize();
});
onBeforeUnmount(() => window.removeEventListener("hashchange", routeChanged));
</script>
<template>
  <div class="top-strip">
    <span>给忙碌日常，留一点喜欢。</span
    ><span>校园生活选物 · 课程演示商城</span>
  </div>
  <header class="site-header">
    <a class="brand" href="#/" aria-label="拾光小铺首页"
      >拾光小铺<small>EVERYDAY FINDS</small></a
    >
    <nav aria-label="主导航">
      <a href="#/" :class="{ current: shop.route === '/' }">日常选物</a
      ><a href="#/cart" :class="{ current: shop.route === '/cart' }"
        >购物袋 <span class="nav-count">{{ shop.cartQuantity }}</span></a
      ><a href="#/orders" :class="{ current: shop.route === '/orders' }"
        >我的订单</a
      ><a
        v-if="shop.user?.role === 'ADMIN'"
        href="#/admin"
        :class="{ current: shop.route === '/admin' }"
        >店铺管理</a
      >
    </nav>
    <div class="account-actions">
      <button
        v-if="!shop.user"
        class="account-button"
        @click="shop.authOpen = true"
      >
        登录 / 注册
      </button>
      <div v-else class="user-menu">
        <span>{{ shop.user.displayName }}</span
        ><button @click="logout">退出</button>
      </div>
      <a
        class="bag-button"
        href="#/cart"
        :aria-label="'购物袋，' + shop.cartQuantity + '件商品'"
        ><svg width="19" height="22" viewBox="0 0 24 26" aria-hidden="true">
          <path
            d="M4 8H20L22 24H2L4 8ZM8 9V6C8 1 16 1 16 6V9"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          /></svg
        ><span class="bag-label">购物袋</span
        ><span>{{ shop.cartQuantity }}</span></a
      >
    </div>
  </header>
  <main class="app-main">
    <HomeView v-if="shop.route === '/'" />
    <div v-else-if="!shop.ready" class="state">正在打开你的拾光空间…</div>
    <section v-else-if="!shop.user" class="sign-in-prompt">
      <span class="kicker">YOUR LITTLE CORNER</span>
      <h1>登录，继续这段拾光。</h1>
      <p>购物袋和订单会跟随你的账号保存。</p>
      <button class="primary" @click="shop.authOpen = true">
        登录 / 注册 ↗</button
      ><a href="#/">先随便逛逛</a>
    </section>
    <CartView v-else-if="shop.route === '/cart'" /><OrdersView
      v-else-if="shop.route === '/orders'"
    /><AdminView
      v-else-if="shop.route === '/admin' && shop.user.role === 'ADMIN'"
    />
    <section v-else class="sign-in-prompt">
      <h1>这一页暂时不在这里。</h1>
      <a class="primary" href="#/">回到小铺</a>
    </section>
  </main>
  <aside
    v-if="shop.cartQuantity > 0 && shop.route !== '/cart'"
    class="cart-dock"
    aria-label="购物袋快捷入口"
  >
    <div>
      <strong>{{ shop.cartQuantity }} 件已在购物袋</strong
      ><span>随时查看，确认后再下单</span>
    </div>
    <a href="#/cart" class="primary"
      >查看购物袋并结算 <span aria-hidden="true">↗</span></a
    >
  </aside>
  <footer class="site-footer">
    <div>
      <a class="footer-brand" href="#/">拾光小铺</a>
      <p>小物有用，日常有光。</p>
    </div>
    <div>
      <p>课程演示项目，无实际扣款与物流。</p>
      <p>
        文具商品资料来自 MUJI 官方商店；真实书目来自
        <a
          href="https://openlibrary.org"
          target="_blank"
          rel="noopener noreferrer"
          >Open Library ↗</a
        >。详情中可查看来源。
      </p>
    </div>
    <span class="footer-edition">MADE FOR EVERYDAY JOY</span>
  </footer>
  <AuthDialog /><Transition name="toast"
    ><div
      v-if="shop.toast"
      class="toast-message"
      :class="shop.toastKind"
      role="status"
    >
      <span aria-hidden="true">{{
        shop.toastKind === "error" ? "!" : "✓"
      }}</span
      >{{ shop.toast
      }}<button aria-label="关闭提示" @click="shop.toast = ''">×</button>
    </div></Transition
  >
</template>
