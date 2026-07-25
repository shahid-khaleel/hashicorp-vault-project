// Toggles a masked secret value between hidden ("••••") and its real
// value, which was rendered server-side into the data-secret attribute.
document.querySelectorAll(".toggle-secret").forEach(function (button) {
  button.addEventListener("click", function () {
    var valueEl = button.previousElementSibling;
    var isHidden = valueEl.textContent.indexOf("•") !== -1;
    if (isHidden) {
      valueEl.textContent = valueEl.dataset.secret;
      button.textContent = "Hide";
    } else {
      valueEl.textContent = "••••••••••••";
      button.textContent = "Show";
    }
  });
});
