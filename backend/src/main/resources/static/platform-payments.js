import {action, bindForm, canEdit, categories, date, esc, field, filters, heading, money,
    options, pagination, status, statuses, table, PAYMENT_PATH, MERCHANT_PATH} from "./platform-ui.js";

const yesNo = {true: "Да", false: "Нет"};
const operations = {
    ARCHIVE: "Архивировать", RESTORE: "Восстановить", CHANGE_STATUS: "Изменить статус",
    ADD_TAG: "Добавить тег", REMOVE_TAG: "Удалить тег"
};
const paymentFilters = ["q", "merchantId", "status", "needsReview", "amountCategory", "tag",
    "archived", "amount", "amountOp", "sort", "direction"];

function tags(data, payment) {
    const raw = data.get("tags");
    if (payment && raw === payment.tags.join("\n")) return payment.tags;
    return [...new Set(raw.split("\n").map((tag) => tag.trim()).filter(Boolean))];
}

function paymentForm(merchants, payment) {
    const financial = !payment || (!payment.cloudPayments && payment.status === "CREATED");
    return `<form id="payment-form" class="cabinet-form">
        <div class="form-grid">
            ${!payment ? field("Магазин", `<select name="merchantId" required>${options(
                Object.fromEntries(merchants.filter((item) => item.active).map((item) => [item.id, item.name])), "", "Выберите магазин"
            )}</select>`) : ""}
            ${!payment ? field("Номер заказа", '<input name="orderNumber" maxlength="100">') : ""}
            ${field("Сумма в рублях", `<input name="amount" type="number" min="0.01" max="9999999999.99" step="0.01" value="${esc(payment?.amount)}" ${financial ? "" : "readonly"} required>`)}
            ${payment ? field("Статус", `<select name="status" ${financial ? "" : "disabled"}>${options(statuses, payment.status)}</select>`) : ""}
        </div>
        ${field("Описание", `<textarea name="description" rows="3" maxlength="${Math.max(500, payment?.description?.length ?? 0)}" required>${esc(payment?.description)}</textarea>`)}
        ${field("Теги, по одному на строку", `<textarea name="tags" rows="2">${esc(payment?.tags.join("\n"))}</textarea>`)}
        <label class="cabinet-check"><input name="needsReview" type="checkbox" ${payment?.needsReview ? "checked" : ""}> На проверке</label>
        <button class="btn btn-primary" type="submit">${payment ? "Сохранить изменения" : "Создать платёж"}</button>
    </form>`;
}

function paymentFields(data, payment) {
    return {
        amount: data.get("amount"),
        description: data.get("description"),
        needsReview: data.has("needsReview"),
        tags: tags(data, payment)
    };
}

function filterForm(params, merchants) {
    const value = (name) => esc(params.get(name));
    return `<form id="payment-filters" class="cabinet-filter-grid">
        ${field("Поиск", `<input name="q" value="${value("q")}" maxlength="120" placeholder="Номер, описание или магазин">`)}
        ${field("Магазин", `<select name="merchantId">${options(Object.fromEntries(merchants.map((item) => [item.id, item.name])), params.get("merchantId"), "Все")}</select>`)}
        ${field("Статус", `<select name="status">${options(statuses, params.get("status"), "Все")}</select>`)}
        ${field("Список", `<select name="archived">${options({false: "Активные", true: "Архив"}, params.get("archived") || "false")}</select>`)}
        ${field("На проверке", `<select name="needsReview">${options(yesNo, params.get("needsReview"), "Все")}</select>`)}
        ${field("Категория суммы", `<select name="amountCategory">${options(categories, params.get("amountCategory"), "Все")}</select>`)}
        ${field("Тег", `<input name="tag" value="${value("tag")}" maxlength="40">`)}
        ${field("Сравнение", `<select name="amountOp">${options({EQ: "Равно", GT: "Больше", GTE: "Не меньше", LT: "Меньше", LTE: "Не больше"}, params.get("amountOp") || "EQ")}</select>`)}
        ${field("Сумма в рублях", `<input type="number" name="amount" min="0" max="9999999999.99" step="0.01" value="${value("amount")}">`)}
        ${field("Сортировать", `<select name="sort">${options({CREATED_AT: "Создание", UPDATED_AT: "Изменение", AMOUNT: "Сумма", NUMBER: "Номер"}, params.get("sort") || "CREATED_AT")}</select>`)}
        ${field("Порядок", `<select name="direction">${options({DESC: "По убыванию", ASC: "По возрастанию"}, params.get("direction") || "DESC")}</select>`)}
        <div class="actions cabinet-filter-actions"><button class="btn btn-primary" type="submit">Применить</button><a class="btn" href="#payments">Сбросить</a></div>
    </form>`;
}

function bulkForm(archived) {
    const allowed = archived ? {RESTORE: operations.RESTORE}
        : {ARCHIVE: operations.ARCHIVE, CHANGE_STATUS: operations.CHANGE_STATUS,
            ADD_TAG: operations.ADD_TAG, REMOVE_TAG: operations.REMOVE_TAG};
    return `<form id="bulk-form" class="cabinet-inline">
        ${field("Действие с выбранными", `<select name="operation">${options(allowed, archived ? "RESTORE" : "ARCHIVE")}</select>`)}
        <div id="bulk-status" hidden>${field("Статус", `<select name="status">${options(statuses, "COMPLETED")}</select>`)}</div>
        <div id="bulk-tag" hidden>${field("Тег", '<input name="tag" maxlength="40">')}</div>
        <button class="btn" type="submit">Выполнить</button>
    </form>`;
}

function csvForm() {
    return `<details class="card panel cabinet-details"><summary>Импорт CSV</summary>
        <form id="csv-form" class="cabinet-form">
            ${field("Режим импорта", `<select name="mode">${options({CREATE_ONLY: "Только новые", UPSERT: "Создать или обновить по номеру заказа"}, "CREATE_ONLY")}</select>`)}
            ${field("Файл CSV", '<input name="file" type="file" accept=".csv,text/csv" required>')}
            <div class="actions"><button class="btn" type="submit">Проверить файл</button>
            <button class="btn btn-primary" id="csv-apply" type="button" disabled>Применить импорт</button></div>
            <div id="csv-report" role="status"></div>
        </form>
    </details>`;
}

export async function paymentsPage(context) {
    const {api, user, params, content} = context;
    const [data, merchants] = await Promise.all([
        api(PAYMENT_PATH + "?" + params), api(MERCHANT_PATH)
    ]);
    const editable = canEdit(user);
    const archived = params.get("archived") === "true";
    const rows = data.items.map((item) => `<tr>
        ${editable ? `<td><input type="checkbox" data-pick="${esc(item.id)}" aria-label="Выбрать платёж ${esc(item.number)}"></td>` : ""}
        <td><a href="#payments/${encodeURIComponent(item.id)}">№ ${esc(item.number)}</a></td>
        <td>${esc(item.orderNumber || "—")}</td><td>${esc(item.merchant.name)}</td>
        <td>${money(item.amount)}</td><td>${status(item.status)}</td>
        <td>${item.needsReview ? "Да" : "—"}</td><td>${esc(date(item.createdAt))}</td></tr>`);
    return {
        html: `${heading("Платежи", '<button class="btn" type="button" id="csv-export">Экспорт по фильтрам</button>')}
            <section class="card panel">${filterForm(params, merchants)}</section>
            ${editable && !archived ? `<details class="card panel cabinet-details"><summary>Создать ручной платёж</summary>${paymentForm(merchants)}</details>` : ""}
            ${editable ? csvForm() : ""}
            <section class="card table-card cabinet-list">
                ${editable && rows.length ? bulkForm(archived) : ""}
                ${table([...(editable ? ["Выбор"] : []), "Платёж", "Заказ", "Магазин", "Сумма", "Статус", "На проверке", "Создан"], rows)}
                ${pagination("payments", params, data)}
            </section>`,
        bind() {
            filters(content.querySelector("#payment-filters"), "payments");
            const create = content.querySelector("#payment-form");
            if (create) bindForm(context, create, async (values) => {
                const item = await api(PAYMENT_PATH, {method: "POST", body: {
                    ...paymentFields(values), merchantId: Number(values.get("merchantId")),
                    orderNumber: values.get("orderNumber")
                }});
                location.hash = "#payments/" + encodeURIComponent(item.id);
            });
            bindBulk(context, data.items);
            bindCsv(context);
        }
    };
}

function bindBulk(context, items) {
    const {content, api, reload} = context;
    const form = content.querySelector("#bulk-form");
    if (!form) return;
    form.elements.operation.addEventListener("change", () => {
        const operation = form.elements.operation.value;
        content.querySelector("#bulk-status").hidden = operation !== "CHANGE_STATUS";
        const usesTag = operation === "ADD_TAG" || operation === "REMOVE_TAG";
        content.querySelector("#bulk-tag").hidden = !usesTag;
        form.elements.tag.required = usesTag;
    });
    bindForm(context, form, async (data) => {
        const selected = new Set([...content.querySelectorAll("[data-pick]:checked")].map((input) => input.dataset.pick));
        if (!selected.size) throw new Error("Выберите хотя бы один платёж.");
        const operation = data.get("operation");
        const body = {operation, items: items.filter((item) => selected.has(item.id)).map(({id, version}) => ({id, version}))};
        if (operation === "CHANGE_STATUS") body.status = data.get("status");
        if (operation === "ADD_TAG" || operation === "REMOVE_TAG") body.tag = data.get("tag");
        const result = await api(PAYMENT_PATH + "/bulk", {method: "POST", body});
        const errors = result.errors.map((error) => {
            const item = items.find((entry) => entry.id === error.id);
            return `№ ${item?.number ?? error.id} — ${error.message}`;
        });
        await reload(`Выполнено: ${result.successes.length}. Ошибок: ${errors.length}.` +
            (errors.length ? "\n" + errors.join("\n") : ""), Boolean(errors.length));
    });
}

function bindCsv(context) {
    const {api, content, params, reload} = context;
    const download = content.querySelector("#csv-export");
    download.addEventListener("click", () => action(context, download, async () => {
        const query = new URLSearchParams([...params].filter(([key]) => paymentFilters.includes(key)));
        const blob = await api(PAYMENT_PATH + "/export?" + query, {download: true, accept: "text/csv"});
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = "payments.csv";
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }));
    const form = content.querySelector("#csv-form");
    if (!form) return;
    const apply = form.querySelector("#csv-apply");
    const report = form.querySelector("#csv-report");
    let reviewedFile;
    let revision = 0;
    function showReport(result) {
        report.innerHTML = `<p>Корректных строк: ${esc(result.validRows)}. Ошибок: ${esc(result.invalidRows)}.</p>
            ${result.errors.length ? `<ul>${result.errors.map((error) =>
                `<li>Строка ${esc(error.row)} — ${esc(error.message)}</li>`).join("")}</ul>` : ""}`;
    }
    form.addEventListener("change", () => {
        revision++;
        reviewedFile = undefined;
        apply.disabled = true;
        report.textContent = "";
    });
    bindForm(context, form, async (body) => {
        const version = ++revision;
        apply.disabled = true;
        reviewedFile = undefined;
        const result = await api(PAYMENT_PATH + "/import/preview", {method: "POST", body});
        if (version !== revision) return;
        showReport(result);
        if (!result.invalidRows) {
            reviewedFile = body;
            apply.disabled = false;
        }
    });
    apply.addEventListener("click", () => {
        if (!reviewedFile) return;
        const version = revision;
        action(context, apply, async () => {
            try {
                const result = await api(PAYMENT_PATH + "/import/apply", {method: "POST", body: reviewedFile});
                await reload(`Создано платежей: ${result.created}. Обновлено: ${result.updated}.`);
            } catch (error) {
                if (error.status !== 422 || !error.body?.errors || version !== revision) throw error;
                showReport(error.body);
                reviewedFile = undefined;
            }
        }).finally(() => { apply.disabled = !reviewedFile; });
    });
}

function notesView(notes, user, writable) {
    const controls = (note) => writable && (user.role === "ADMIN" || user.id === note.authorId);
    return `<section class="card panel cabinet-details"><h2>Заметки</h2>
        ${notes.length ? notes.map((note) => `<article class="cabinet-note">
            <div><strong>${esc(note.authorName)}</strong> · ${esc(date(note.createdAt))}${note.pinned ? " · Закреплена" : ""}</div>
            <p>${esc(note.text)}</p>
            ${controls(note) ? `<details><summary>Изменить заметку</summary>${noteForm(note)}</details>
                <button class="btn" type="button" data-delete-note="${esc(note.id)}">Удалить заметку</button>` : ""}
        </article>`).join("") : '<p class="empty">Заметок пока нет.</p>'}
        ${writable ? `<details><summary>Добавить заметку</summary>${noteForm()}</details>` : ""}
    </section>`;
}

function noteForm(note) {
    return `<form class="cabinet-form" data-note="${esc(note?.id)}">
        ${field("Текст заметки", `<textarea name="text" maxlength="2000" rows="3" required>${esc(note?.text)}</textarea>`)}
        <label class="cabinet-check"><input name="pinned" type="checkbox" ${note?.pinned ? "checked" : ""}> Закрепить</label>
        <button class="btn btn-primary" type="submit">${note ? "Сохранить" : "Добавить"}</button>
    </form>`;
}

export async function paymentPage(context) {
    const {api, user, content, id, reload} = context;
    const path = PAYMENT_PATH + "/" + encodeURIComponent(id);
    const [payment, notes] = await Promise.all([api(path), api(path + "/notes")]);
    const writable = canEdit(user) && !payment.archived;
    const permanent = user.role === "ADMIN" && payment.archived && !payment.cloudPayments;
    const fields = {
        "Заказ": esc(payment.orderNumber || "—"), "Магазин": esc(payment.merchant.name),
        "Сумма": money(payment.amount), "Статус": status(payment.status),
        "Источник": payment.cloudPayments ? "CloudPayments" : "Ручной платёж",
        "Способ оплаты": esc(payment.paymentMethod || "—"), "Категория": esc(categories[payment.amountCategory]),
        "На проверке": payment.needsReview ? "Да" : "Нет", "Архив": payment.archived ? "Да" : "Нет",
        "Создан": esc(date(payment.createdAt)), "Изменён": esc(date(payment.updatedAt)),
        "Теги": esc(payment.tags.join(", ") || "—"), "UUID": esc(payment.id)
    };
    return {
        html: `${heading("Платёж № " + payment.number, '<a class="btn" href="#payments">К списку</a>')}
            <section class="card panel"><dl class="cabinet-dl">${Object.entries(fields).map(([label, value]) =>
                `<div><dt>${label}</dt><dd>${value}</dd></div>`).join("")}</dl>
                <h2>Описание</h2><p class="cabinet-text">${esc(payment.description)}</p>
                <div class="actions">
                    ${canEdit(user) ? `<button class="btn" type="button" id="archive-action">${payment.archived ? "Восстановить" : "Архивировать"}</button>` : ""}
                    ${permanent ? '<button class="btn cabinet-danger" type="button" id="delete-payment">Удалить окончательно</button>' : ""}
                </div>
            </section>
            ${writable ? `<details class="card panel cabinet-details"><summary>Редактировать платёж</summary>${paymentForm([], payment)}</details>` : ""}
            ${notesView(notes, user, writable)}`,
        bind() {
            const edit = content.querySelector("#payment-form");
            if (edit) bindForm(context, edit, async (data) => {
                await api(path, {method: "PUT", body: {
                    ...paymentFields(data, payment), version: payment.version,
                    status: data.get("status") || payment.status
                }});
                await reload("Изменения сохранены.");
            });
            const archive = content.querySelector("#archive-action");
            archive?.addEventListener("click", () => action(context, archive, async () => {
                await api(path + (payment.archived ? "/restore" : "") + "?version=" + payment.version,
                    {method: payment.archived ? "POST" : "DELETE"});
                await reload(payment.archived ? "Платёж восстановлен." : "Платёж перенесён в архив.");
            }));
            const remove = content.querySelector("#delete-payment");
            remove?.addEventListener("click", () => {
                if (!window.confirm("Окончательно удалить платёж и его заметки?")) return;
                action(context, remove, async () => {
                    await api(path + "/permanent?version=" + payment.version, {method: "DELETE"});
                    location.hash = "#payments?archived=true";
                });
            });
            content.querySelectorAll("[data-note]").forEach((form) => bindForm(context, form, async (data) => {
                const noteId = form.dataset.note;
                await api(path + "/notes" + (noteId ? "/" + encodeURIComponent(noteId) : ""), {
                    method: noteId ? "PUT" : "POST", body: {text: data.get("text"), pinned: data.has("pinned")}
                });
                await reload(noteId ? "Заметка изменена." : "Заметка добавлена.");
            }));
            content.querySelectorAll("[data-delete-note]").forEach((button) => button.addEventListener("click", () => {
                if (!window.confirm("Удалить заметку?")) return;
                action(context, button, async () => {
                    await api(path + "/notes/" + encodeURIComponent(button.dataset.deleteNote), {method: "DELETE"});
                    await reload("Заметка удалена.");
                });
            }));
        }
    };
}
