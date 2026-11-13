const api = async (url, options = {}) => {
    const response = await fetch(url, {
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options
    });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Request failed (${response.status})`);
    return data;
};

const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[character]));

const formatDate = value => new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium', timeStyle: 'short'
}).format(new Date(value));

const setMessage = (message, text, isError = true) => {
    message.textContent = text;
    message.classList.toggle('is-error', isError);
    message.classList.toggle('is-success', !isError);
};

async function updateNavigation() {
    const login = document.querySelector('#login-link');
    if (!login) return;
    try {
        const user = await api('/api/auth/me');
        login.hidden = true;
        document.querySelector('#register-link').hidden = true;
        document.querySelector('#my-bookings-link').hidden = false;
        document.querySelector('#logout-button').hidden = false;
        document.querySelector('#logout-button').addEventListener('click', async () => {
            await api('/api/auth/logout', { method: 'POST' });
            window.location.href = '/';
        });
        if (user.role === 'ADMIN') document.querySelector('#admin-link').hidden = false;
    } catch (_) {
        // Guests see the default login and register links.
    }
}

async function loadHome() {
    const grid = document.querySelector('#movie-grid');
    try {
        const movies = await api('/api/movies');
        grid.innerHTML = movies.length ? movies.map(movie => `
            <a class="movie-card" href="/movies/${movie.id}">
                <img src="${escapeHtml(movie.posterUrl || '')}" alt="Poster for ${escapeHtml(movie.title)}"
                     onerror="this.classList.add('image-missing');this.removeAttribute('src')">
                <div class="movie-card-body"><p class="eyebrow">${escapeHtml(movie.genre)} · ${movie.durationMinutes} min</p>
                    <h3>${escapeHtml(movie.title)}</h3><p class="muted">${escapeHtml(movie.language)}</p>
                    <span class="text-link">View showtimes →</span></div>
            </a>`).join('') : '<p class="muted">No movies have been added yet.</p>';
    } catch (error) {
        grid.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
    }
}

async function loadMovie() {
    const id = document.body.dataset.movieId;
    const detail = document.querySelector('#movie-detail');
    const showList = document.querySelector('#show-list');
    try {
        const [movie, shows] = await Promise.all([api(`/api/movies/${id}`), api(`/api/movies/${id}/shows`)]);
        detail.innerHTML = `<img class="detail-poster" src="${escapeHtml(movie.posterUrl || '')}"
                alt="Poster for ${escapeHtml(movie.title)}" onerror="this.classList.add('image-missing');this.removeAttribute('src')">
            <div><p class="eyebrow">${escapeHtml(movie.genre)} · ${movie.durationMinutes} min · ${escapeHtml(movie.language)}</p>
                <h1>${escapeHtml(movie.title)}</h1><p class="detail-description">${escapeHtml(movie.description)}</p></div>`;
        showList.innerHTML = shows.length ? shows.map(show => `<article class="show-card">
            <div><strong>${escapeHtml(formatDate(show.showTime))}</strong>
                <p class="muted">${escapeHtml(show.theatreName)} · ${escapeHtml(show.city)} · ${escapeHtml(show.screenName)}</p></div>
            <div class="show-action"><span class="price">₹${Number(show.price).toFixed(2)}</span>
                <a class="button button-small" href="/shows/${show.id}/seats">Select seats</a></div></article>`).join('')
            : '<p class="muted">There are no showtimes for this movie yet.</p>';
    } catch (error) {
        detail.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
        showList.replaceChildren();
    }
}

async function loadSeats() {
    const showId = document.body.dataset.showId;
    const grid = document.querySelector('#seat-grid');
    const selected = new Set();
    const summary = document.querySelector('#selection-summary');
    const bookButton = document.querySelector('#book-seats');
    try {
        const seats = await api(`/api/shows/${showId}/seats`);
        grid.innerHTML = seats.map(seat => {
            const booked = seat.status === 'BOOKED';
            return `<button class="seat ${booked ? 'booked' : 'available'}" data-seat-id="${seat.id}"
                aria-label="Row ${escapeHtml(seat.rowLabel)}, seat ${seat.seatNumber}${booked ? ', booked' : ''}"
                ${booked ? 'disabled' : ''}>${escapeHtml(seat.rowLabel)}${seat.seatNumber}</button>`;
        }).join('');
        grid.addEventListener('click', event => {
            const button = event.target.closest('.seat:not(:disabled)');
            if (!button) return;
            const seatId = Number(button.dataset.seatId);
            if (selected.has(seatId)) {
                selected.delete(seatId);
                button.classList.replace('selected', 'available');
            } else {
                selected.add(seatId);
                button.classList.replace('available', 'selected');
            }
            summary.textContent = `${selected.size} seat${selected.size === 1 ? '' : 's'}`;
            bookButton.disabled = selected.size === 0;
        });
        bookButton.addEventListener('click', async () => {
            bookButton.disabled = true;
            try {
                const booking = await api('/api/bookings', {
                    method: 'POST', body: JSON.stringify({ showId: Number(showId), seatIds: [...selected] })
                });
                sessionStorage.setItem('latestBookingId', String(booking.id));
                window.location.href = `/bookings/confirmation/${booking.id}`;
            } catch (error) {
                document.querySelector('#seat-hint').textContent = error.message;
                document.querySelector('#seat-hint').classList.add('is-error');
                bookButton.disabled = selected.size === 0;
            }
        });
    } catch (error) {
        grid.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
    }
}

async function loadConfirmation() {
    const bookingId = document.body.dataset.bookingId;
    const details = document.querySelector('#confirmation-details');
    try {
        const booking = await api(`/api/bookings/${bookingId}`);
        details.innerHTML = `<strong>${escapeHtml(booking.movieTitle)}</strong><br>
            ${escapeHtml(formatDate(booking.showTime))}<br>${escapeHtml(booking.theatreName)} · ${escapeHtml(booking.screenName)}<br>
            Seats: ${escapeHtml(booking.seats.join(', '))}<br>Total: ₹${Number(booking.totalAmount).toFixed(2)}<br>
            Booking #${booking.id}`;
    } catch (error) {
        details.textContent = error.message;
    }
}

async function loadBookings() {
    const list = document.querySelector('#booking-list');
    if (!list.dataset.bound) {
        list.dataset.bound = 'true';
        list.addEventListener('click', async event => {
            const button = event.target.closest('.cancel-booking');
            if (!button || !window.confirm('Cancel this reservation?')) return;
            button.disabled = true;
            try { await api(`/api/bookings/${button.dataset.bookingId}`, { method: 'DELETE' }); await loadBookings(); }
            catch (error) { button.disabled = false; window.alert(error.message); }
        });
    }
    try {
        const user = await api('/api/auth/me');
        const bookings = await api(`/api/users/${user.id}/bookings`);
        list.innerHTML = bookings.length ? bookings.map(booking => `<article class="booking-card">
            <div class="booking-card-top"><div><p class="eyebrow">BOOKING #${booking.id}</p><h2>${escapeHtml(booking.movieTitle)}</h2></div>
                <span class="status ${booking.status.toLowerCase()}">${escapeHtml(booking.status)}</span></div>
            <p class="muted">${escapeHtml(formatDate(booking.showTime))} · ${escapeHtml(booking.theatreName)} · ${escapeHtml(booking.screenName)}</p>
            <p>Seats: ${escapeHtml(booking.seats.join(', ') || '—')} · Total: ₹${Number(booking.totalAmount).toFixed(2)}</p>
            ${booking.status === 'CONFIRMED' ? `<button class="button button-secondary cancel-booking" data-booking-id="${booking.id}">Cancel booking</button>` : ''}
        </article>`).join('') : '<p class="muted">You have no bookings yet. <a href="/">Browse movies</a></p>';
    } catch (error) {
        list.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)} <a href="/login">Log in</a></p>`;
    }
}

function wireAccountForms() {
    const loginForm = document.querySelector('#login-form');
    const registerForm = document.querySelector('#register-form');
    const form = loginForm || registerForm;
    if (!form) return;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.querySelector('#form-message');
        const values = Object.fromEntries(new FormData(form));
        try {
            await api(loginForm ? '/api/auth/login' : '/api/auth/register', {
                method: 'POST', body: JSON.stringify(values)
            });
            window.location.href = '/';
        } catch (error) { setMessage(message, error.message); }
    });
}

async function wireMovieForm() {
    const form = document.querySelector('#movie-form');
    if (!form) return;
    try {
        const user = await api('/api/auth/me');
        if (user.role !== 'ADMIN') throw new Error('Administrator access is required.');
    } catch (error) {
        form.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)} <a href="/login">Log in as an administrator</a></p>`;
        return;
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.querySelector('#form-message');
        const values = Object.fromEntries(new FormData(form));
        values.durationMinutes = Number(values.durationMinutes);
        values.posterUrl = values.posterUrl || null;
        try {
            const movie = await api('/api/movies', { method: 'POST', body: JSON.stringify(values) });
            setMessage(message, `Added “${movie.title}”.`, false);
            form.reset();
        } catch (error) { setMessage(message, error.message); }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    updateNavigation();
    const page = document.body.dataset.page;
    if (page === 'home') loadHome();
    if (page === 'movie') loadMovie();
    if (page === 'seats') loadSeats();
    if (page === 'confirmation') loadConfirmation();
    if (page === 'bookings') loadBookings();
    if (page === 'login' || page === 'register') wireAccountForms();
    if (page === 'admin') wireMovieForm();
});
