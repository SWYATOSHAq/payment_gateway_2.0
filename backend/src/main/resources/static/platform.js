import {api, changePassword, currentUser, login, logout, restoreSession} from "./platform-api.js";
import {auditPage, merchantsPage, overviewPage, usersPage} from "./platform-management.js";
import {paymentPage, paymentsPage} from "./platform-payments.js";
import {action, bindForm, esc, field, notice, roles} from "./platform-ui.js";

const app = document.querySelector("#app");
const navigation = {
    overview: "Обзор", payments: "Платежи", merchants: "Магазины", users: "Пользователи", audit: "Аудит", profile: "Профиль"
};
let renderVersion = 0;

function shell(user, route) {
    const sections = Object.entries(navigation).filter(([key]) =>
        user.role === "ADMIN" || (key !== "users" && key !== "audit"));
    return `<div class="shell"><aside class="sidebar" id="cabinet-sidebar">
        <a href="#overview" class="brand"><span class="brand-mark" aria-hidden="true">◈</span>
        <span class="brand-name">TITAN PAY</span></a>
        <nav class="nav-group cabinet-nav" aria-label="Разделы кабинета">
            ${sections.map(([key, label]) => `<a class="nav-link${key === route ? " active" : ""}" href="#${key}">${label}</a>`).join("")}
        </nav>
        <div class="sidebar-foot"><a class="nav-link" href="/">Тестовая оплата</a></div>
        </aside><button class="scrim" id="cabinet-scrim" type="button" aria-label="Закрыть меню"></button>
        <main class="main"><header class="topbar">
            <button class="btn mobile-menu" id="cabinet-menu" type="button" aria-controls="cabinet-sidebar" aria-expanded="false">Меню</button>
            <div class="cabinet-profile"><strong>${esc(user.displayName)}</strong><small>${esc(roles[user.role])}</small></div>
            <button class="btn" type="button" id="logout">Выйти</button>
        </header><p class="cabinet-notice" data-notice role="status" hidden></p>
        <section id="cabinet-content"></section></main></div>`;
}

function passwordFields(currentLabel) {
    return field(currentLabel, '<input type="password" name="currentPassword" autocomplete="current-password" maxlength="72" required>')
        + field("Новый пароль", '<input type="password" name="newPassword" autocomplete="new-password" minlength="12" maxlength="72" required>');
}

function signInScreen(change = false, message = "") {
    const title = change ? "Смена временного пароля" : "Вход в кабинет";
    app.innerHTML = `<main class="cabinet-auth card panel"><a href="/" class="brand-name">TITAN PAY</a>
        <h1>${title}</h1>
        <p class="cabinet-notice" data-notice role="status" hidden></p>
        <form id="account-form" class="cabinet-form">
            ${change ? passwordFields("Временный пароль")
                : field("Логин", '<input name="login" autocomplete="username" maxlength="120" required>')
                    + field("Пароль", '<input type="password" name="password" autocomplete="current-password" maxlength="72" required>')}
            <button class="btn btn-primary" type="submit">${change ? "Сменить пароль" : "Войти"}</button>
        </form>
        ${change ? '<button class="btn" type="button" id="logout">Выйти</button>' : ""}
    </main>`;
    const context = {scope: app, handleSession: () => false};
    notice(app, message, Boolean(message));
    bindForm(context, app.querySelector("form"), async (data) => {
        const submit = change ? changePassword : login;
        await submit(Object.fromEntries(data));
        await render();
    });
    bindLogout(context);
}

function bindLogout(context) {
    const button = app.querySelector("#logout");
    button?.addEventListener("click", () => action(context, button, async () => {
        try {
            await logout();
            await render();
        } catch {
            signInScreen(false, "Сеанс закрыт в этом браузере. Сервер недоступен.");
        }
    }));
}

function bindMenu() {
    const sidebar = app.querySelector("#cabinet-sidebar");
    const scrim = app.querySelector("#cabinet-scrim");
    const menu = app.querySelector("#cabinet-menu");
    function setOpen(open) {
        sidebar.classList.toggle("open", open);
        scrim.classList.toggle("show", open);
        menu.setAttribute("aria-expanded", String(open));
    }
    menu.addEventListener("click", () => setOpen(!sidebar.classList.contains("open")));
    scrim.addEventListener("click", () => { setOpen(false); menu.focus(); });
    app.querySelector(".shell").addEventListener("keydown", (event) => {
        if (event.key === "Escape" && sidebar.classList.contains("open")) {
            setOpen(false);
            menu.focus();
        }
    });
}

function profilePage(context) {
    return {
        html: `<header class="page-head"><h1>Профиль</h1></header>
            <section class="card panel"><p>${esc(context.user.displayName)} · ${esc(context.user.login)}</p>
                <form class="cabinet-form">${passwordFields("Текущий пароль")}
                    <button class="btn btn-primary" type="submit">Сменить пароль</button>
                </form></section>`,
        bind() {
            bindForm(context, context.content.querySelector("form"), async (data) => {
                await changePassword(Object.fromEntries(data));
                await context.reload("Пароль изменён.");
            });
        }
    };
}

async function render(message = "", isError = false) {
    const version = ++renderVersion;
    const user = currentUser();
    if (!user || user.mustChangePassword) {
        signInScreen(Boolean(user), message);
        return;
    }

    const [path, query] = location.hash.slice(1).split("?");
    const [requested, id] = path.split("/");
    const route = Object.hasOwn(navigation, requested) ? requested : "overview";
    const params = new URLSearchParams(query);
    const handler = {
        overview: overviewPage, payments: id ? paymentPage : paymentsPage,
        merchants: merchantsPage, users: usersPage, audit: auditPage, profile: profilePage
    }[route];

    app.innerHTML = shell(user, route);
    const scope = app.querySelector("main");
    const content = scope.querySelector("#cabinet-content");
    const context = {
        api, user, scope, content, params, id,
        reload(text, error) {
            if (version === renderVersion) return render(text, error);
        },
        handleSession() {
            if (version !== renderVersion) return true;
            if (!currentUser()) {
                render("Войдите заново.");
                return true;
            }
            return false;
        }
    };
    bindMenu();
    bindLogout(context);
    content.innerHTML = '<p role="status" class="empty">Загрузка…</p>';
    notice(scope, message, isError);
    try {
        if ((route === "users" || route === "audit") && user.role !== "ADMIN") {
            content.innerHTML = '<p class="empty">Этот раздел доступен администратору.</p>';
            return;
        }
        const view = await handler(context);
        if (version !== renderVersion) return;
        content.innerHTML = view.html;
        view.bind?.();
    } catch (error) {
        if (!context.handleSession()) {
            content.innerHTML = '<button type="button" class="btn" id="retry">Повторить</button>';
            notice(scope, error.message || "Не удалось загрузить данные.", true);
            content.querySelector("#retry").addEventListener("click", () => render());
        }
    }
}

window.addEventListener("hashchange", () => render());
app.innerHTML = '<p class="empty" role="status">Открываем кабинет…</p>';
restoreSession().then(() => render()).catch(() => render("Сессию не удалось восстановить. Войди снова.", true));
