const form = document.querySelector("#checkout");
const message = document.querySelector("#message");
const payment = document.querySelector("#payment");
const link = document.querySelector("#payment-link");
const submit = form.querySelector("button");
const refresh = document.querySelector("#refresh");
let paymentId;

async function request(url, options) {
    const response = await fetch(url, options);
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "Запрос не выполнен");
    }
    return body;
}

function showPayment(result) {
    paymentId = result.id;
    const labels = {
        CREATED: "Ожидает оплаты",
        COMPLETED: "Оплачен",
        CANCELLED: "Отменён"
    };
    message.textContent = labels[result.status] || result.status;
    link.hidden = !result.paymentUrl || result.status !== "CREATED";
    if (!link.hidden) {
        link.href = result.paymentUrl;
    } else {
        link.removeAttribute("href");
    }
    payment.hidden = false;
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    payment.hidden = true;
    submit.disabled = true;
    message.textContent = "Создаём ссылку…";
    const data = new FormData(form);
    try {
        showPayment(await request("/api/payments", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Authorization: "Bearer " + data.get("key")
            },
            body: JSON.stringify({
                orderNumber: data.get("orderNumber"),
                amount: data.get("amount")
            })
        }));
    } catch (error) {
        message.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
});

refresh.addEventListener("click", async () => {
    refresh.disabled = true;
    submit.disabled = true;
    try {
        showPayment(await request("/api/payments/" + paymentId));
    } catch (error) {
        message.textContent = error.message;
    } finally {
        refresh.disabled = false;
        submit.disabled = false;
    }
});
