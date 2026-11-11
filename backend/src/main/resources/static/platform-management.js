import {bindForm, categories, date, esc, field, filters, heading, MERCHANT_PATH,
    money, options, pagination, roles, statuses, table} from "./platform-ui.js";

export async function overviewPage({api}) {
    const data = await api("/api/analytics/summary");
    const metrics = [
        ["Всего платежей", data.total], ["Активные", data.active], ["В архиве", data.archived],
        ["На проверке", data.needsReview], ["Средняя сумма", money(data.averageAmount)]
    ];
    const counts = (values, labels) => Object.entries(values).map(([key, count]) =>
        `<tr><td>${esc(labels[key] || key)}</td><td>${esc(count)}</td></tr>`);
    const shops = data.byMerchant.map((item) => `<tr><td>${esc(item.name)}</td><td>${esc(item.count)}</td></tr>`);
    return {
        html: `${heading("Обзор", '<a class="btn btn-primary" href="#payments">Открыть платежи</a>')}
            <div class="cabinet-metrics">${metrics.map(([label, value]) =>
                `<article class="card metric-tile"><span>${label}</span><strong>${esc(value)}</strong></article>`).join("")}</div>
            <div class="cabinet-summary">
                <section class="card panel"><h2>По магазинам</h2>${table(["Магазин", "Платежи"], shops)}</section>
                <section class="card panel"><h2>По статусам</h2>${table(["Статус", "Платежи"], counts(data.byStatus, statuses))}</section>
                <section class="card panel"><h2>По суммам</h2>${table(["Категория", "Платежи"], counts(data.byAmountCategory, categories))}</section>
            </div>`
    };
}

function merchantForm(item) {
    return `<form class="cabinet-form" data-merchant="${esc(item?.id)}">
        ${field("Название", `<input name="name" maxlength="100" value="${esc(item?.name)}" required>`)}
        ${field("Сайт", `<input name="website" type="url" maxlength="255" value="${esc(item?.website)}" required>`)}
        ${item ? `<label class="cabinet-check"><input name="active" type="checkbox" ${item.active ? "checked" : ""}> Включён</label>` : ""}
        <button class="btn btn-primary" type="submit">${item ? "Сохранить" : "Создать магазин"}</button>
    </form>`;
}

export async function merchantsPage(context) {
    const {api, user, content, reload} = context;
    const data = await api(MERCHANT_PATH);
    const admin = user.role === "ADMIN";
    const rows = data.map((item) => `<tr><td>${esc(item.name)}</td><td>${esc(item.website)}</td>
        <td>${item.active ? "Включён" : "Отключён"}</td>
        ${admin ? `<td><details><summary>Изменить</summary>${merchantForm(item)}</details></td>` : ""}</tr>`);
    return {
        html: `${heading("Магазины")}
            ${admin ? `<details class="card panel cabinet-details"><summary>Добавить магазин</summary>${merchantForm()}</details>` : ""}
            <section class="card table-card">${table(["Название", "Сайт", "Состояние", ...(admin ? ["Действия"] : [])], rows)}</section>`,
        bind() {
            content.querySelectorAll("[data-merchant]").forEach((form) => bindForm(context, form, async (data) => {
                const id = form.dataset.merchant;
                const body = {name: data.get("name"), website: data.get("website")};
                if (id) body.active = data.has("active");
                await api(MERCHANT_PATH + (id ? "/" + encodeURIComponent(id) : ""), {
                    method: id ? "PUT" : "POST", body
                });
                await reload(id ? "Магазин обновлён." : "Магазин создан.");
            }));
        }
    };
}

function passwordField() {
    return field("Временный пароль", '<input name="temporaryPassword" type="password" minlength="12" maxlength="72" autocomplete="new-password" required>');
}

function userForm(item, user) {
    const self = item && item.id === user.id;
    return `<form class="cabinet-form" data-user="${esc(item?.id)}">
        ${field("Имя", `<input name="displayName" maxlength="120" value="${esc(item?.displayName)}" required>`)}
        ${field("Логин", `<input name="login" maxlength="120" value="${esc(item?.login)}" required>`)}
        ${field("Роль", `<select name="role" ${self ? "disabled" : ""}>${options(roles, item?.role || "VIEWER")}</select>`)}
        ${item ? `<label class="cabinet-check"><input name="active" type="checkbox" ${item.active ? "checked" : ""} ${self ? "disabled" : ""}> Активен</label>` : passwordField()}
        <button class="btn btn-primary" type="submit">${item ? "Сохранить" : "Создать пользователя"}</button>
    </form>`;
}

export async function usersPage(context) {
    const {api, user, params, content, reload} = context;
    const path = "/api/admin/users";
    const data = await api(path + "?" + params);
    const rows = data.items.map((item) => `<tr><td>${esc(item.displayName)}</td><td>${esc(item.login)}</td>
        <td>${esc(roles[item.role])}</td><td>${item.active ? "Активен" : "Отключён"}${item.mustChangePassword ? " · Временный пароль" : ""}</td>
        <td><details><summary>Изменить</summary>${userForm(item, user)}</details>
            <details><summary>Сбросить пароль</summary>
                <form class="cabinet-form" data-reset-user="${esc(item.id)}">${passwordField()}
                    <button type="submit" class="btn">Установить пароль</button>
                </form>
            </details></td></tr>`);
    return {
        html: `${heading("Пользователи")}
            <details class="card panel cabinet-details"><summary>Добавить пользователя</summary>${userForm(null, user)}</details>
            <section class="card table-card">${table(["Имя", "Логин", "Роль", "Состояние", "Действия"], rows)}
            ${pagination("users", params, data)}</section>`,
        bind() {
            content.querySelectorAll("[data-user]").forEach((form) => bindForm(context, form, async (values) => {
                const id = form.dataset.user;
                const body = Object.fromEntries(values);
                if (id) {
                    body.active = id === user.id || values.has("active");
                    body.role = values.get("role") || user.role;
                }
                await api(path + (id ? "/" + encodeURIComponent(id) : ""), {method: id ? "PUT" : "POST", body});
                await reload(id ? "Пользователь обновлён." : "Пользователь создан.");
            }));
            content.querySelectorAll("[data-reset-user]").forEach((form) => bindForm(context, form, async (values) => {
                const id = form.dataset.resetUser;
                await api(path + "/" + encodeURIComponent(id) + "/reset-password", {
                    method: "POST", body: Object.fromEntries(values)
                });
                await reload("Временный пароль установлен.");
            }));
        }
    };
}

export async function auditPage({api, params, content}) {
    const data = await api("/api/admin/audit?" + params);
    const rows = data.items.map((item) => `<tr><td>${esc(date(item.occurredAt))}</td><td>${esc(item.actorLogin || "Система")}</td>
        <td><strong>${esc(item.description)}</strong><br><code>${esc(item.eventType)}</code></td>
        <td>${esc(item.entityType)}<br><code>${esc(item.entityId)}</code></td><td><code>${esc(item.correlationId)}</code></td></tr>`);
    return {
        html: `${heading("Аудит")}
            <section class="card panel"><form id="audit-filters" class="cabinet-inline">
                ${field("Поиск по журналу", `<input name="q" maxlength="120" value="${esc(params.get("q"))}">`)}
                ${field("Порядок", `<select name="sort">${options({DESC: "Сначала новые", ASC: "Сначала старые"}, params.get("sort") || "DESC")}</select>`)}
                <button type="submit" class="btn btn-primary">Найти</button>
            </form></section>
            <section class="card table-card cabinet-list">${table(["Время", "Пользователь", "Событие", "Объект", "Correlation ID"], rows)}
            ${pagination("audit", params, data)}</section>`,
        bind() { filters(content.querySelector("#audit-filters"), "audit"); }
    };
}
