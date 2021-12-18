async function submitForm() {
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

      hcaptcha.reset()

      const response = await fetch('/request', {
             method: 'POST',
             body: formData
       });

       const code = await response.text();

       Swal.close();

       eval(code);

}