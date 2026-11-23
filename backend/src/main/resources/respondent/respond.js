// Respondent page for /r/<token>: loads the questions from /r/<token>/form and posts the answers
// to /r/<token>/responses. Plain DOM, no dependencies; everything stays under /r/.
(function () {
  'use strict';
  var app = document.getElementById('app');
  var token = app.getAttribute('data-token');
  var base = '/r/' + encodeURIComponent(token);

  function el(tag, attrs, text) {
    var e = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    if (text != null) e.textContent = text;
    return e;
  }

  function message(title, text) {
    app.textContent = '';
    app.appendChild(el('h1', null, title));
    app.appendChild(el('p', null, text));
  }

  function errorText(res, body) {
    if (res.status === 410) return 'This survey is closed and no longer accepts answers.';
    if (res.status === 404) return 'This survey link is not valid.';
    if (res.status === 409) return 'You have already answered this survey.';
    if (res.status === 401) return 'Please sign in to answer this survey.';
    return (body && body.message) || ('Something went wrong (HTTP ' + res.status + ').');
  }

  function render(form) {
    app.textContent = '';
    app.appendChild(el('h1', null, form.title));
    if (form.description) app.appendChild(el('p', { 'class': 'description' }, form.description));
    var f = el('form', { novalidate: '' });
    form.questions.forEach(function (q, i) {
      var fs = el('fieldset', { 'class': 'question' });
      var legend = el('legend', null, (i + 1) + '. ' + q.text);
      if (q.required) legend.appendChild(el('span', { 'class': 'req', title: 'required' }, ' *'));
      fs.appendChild(legend);
      if (q.type === 'TEXT') {
        var ta = el('textarea', { name: 'q' + q.id, maxlength: '2000', rows: '3' });
        if (q.required) ta.required = true;
        fs.appendChild(ta);
      } else {
        q.options.forEach(function (o) {
          var id = 'q' + q.id + 'o' + o.id;
          var label = el('label', { 'for': id, 'class': 'option' });
          var input = el('input', { type: 'radio', id: id, name: 'q' + q.id, value: String(o.id) });
          if (q.required) input.required = true;
          label.appendChild(input);
          label.appendChild(document.createTextNode(' ' + o.text));
          fs.appendChild(label);
        });
      }
      f.appendChild(fs);
    });
    var status = el('p', { 'class': 'status', role: 'status' });
    var button = el('button', { type: 'submit' }, 'Submit');
    f.appendChild(button);
    f.appendChild(status);
    f.addEventListener('submit', function (ev) {
      ev.preventDefault();
      if (!f.reportValidity()) return;
      var answers = form.questions.map(function (q) {
        if (q.type === 'TEXT') {
          return { questionId: q.id, text: f.elements['q' + q.id].value };
        }
        var checked = f.querySelector('input[name="q' + q.id + '"]:checked');
        return { questionId: q.id, optionId: checked ? Number(checked.value) : null };
      });
      button.disabled = true;
      status.textContent = 'Sending…';
      fetch(base + '/responses', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-Respondent': '1' },
        body: JSON.stringify({ answers: answers })
      }).then(function (res) {
        if (res.ok) { message('Thank you!', 'Your answers have been recorded.'); return; }
        return res.json().catch(function () { return null; }).then(function (body) {
          button.disabled = false;
          status.textContent = errorText(res, body);
        });
      }).catch(function () {
        button.disabled = false;
        status.textContent = 'Network error, please try again.';
      });
    });
    app.appendChild(f);
  }

  fetch(base + '/form', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(function (res) {
      if (res.ok) return res.json().then(render);
      return res.json().catch(function () { return null; }).then(function (body) {
        message('Survey unavailable', errorText(res, body));
      });
    })
    .catch(function () { message('Survey unavailable', 'Network error, please reload.'); });
})();
