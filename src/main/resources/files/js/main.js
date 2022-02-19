async function submitAddressForm() {

      const mainForm = document.getElementById('mainForm');
      const formData = new FormData(mainForm);

      var newLocation = "/?address=" + formData.get("address")

      if (formData.get("chain") != null) {
         newLocation = newLocation + "&" + formData.get("chain")
      }

      window.location = newLocation
}

async function submitFinalForm() {
      const mainForm = document.getElementById('mainForm');
      const formData = new FormData(mainForm);

      // Workaround, hCaptcha adds two keys with same value
      formData.delete('g-recaptcha-response');

      Swal.fire({
        title: "Please wait",
        imageUrl: "/static/images/progress.gif",
        showConfirmButton: false,
        allowOutsideClick: false
      });

      try { hcaptcha.reset() } catch(e) { if (e.cause != 'missing-captcha') throw e; }

      const response = await fetch('/request', {
             method: 'POST',
             body: formData
       });

       const code = await response.text();

       Swal.close();

       eval(code);

}
