// Small UI helpers: mobile menu, delete confirmations, status-modal preselect.
document.addEventListener('click', function (e) {
  var toggle = e.target.closest('[data-nav-toggle]');
  if (toggle) { document.body.classList.toggle('nav-open'); return; }
  if (e.target.closest('.nav-scrim')) { document.body.classList.remove('nav-open'); }
});

document.addEventListener('submit', function (e) {
  var msg = e.target.getAttribute('data-confirm');
  if (msg && !window.confirm(msg)) e.preventDefault();
});

// Live character counter for fields with data-count="max"
document.querySelectorAll('[data-count]').forEach(function (field) {
  var out = document.querySelector('[data-count-for="' + field.id + '"]');
  if (!out) return;
  var max = field.getAttribute('data-count');
  var update = function () { out.textContent = field.value.length + ' / ' + max; };
  field.addEventListener('input', update);
  update();
});

// Activity form: choosing an activity type pre-selects its usual channel (still changeable)
(function () {
  var channel = document.getElementById('channel');
  if (!channel) return;
  document.querySelectorAll('input[name="activityType"][data-channel]').forEach(function (radio) {
    radio.addEventListener('change', function () {
      if (radio.checked) channel.value = radio.getAttribute('data-channel');
    });
  });
})();
