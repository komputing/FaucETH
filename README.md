# FaucETH

Faucet for EVM chains

# Why

I had a lot of problems with existing simple faucets (the ones that do not force you to use some centralized service like twitter) and had the urge to create one that does not suffer from these problems for a while. 
Then [a tweet of pari](https://twitter.com/parithosh_j/status/1471888173366235143) in context of faucet problems for the kintsugi merge testnet gave the spark to finally code the faucet.

# What

This faucet uses hCaptcha instead of reCaptcha to not feed the google.

# How

Either download the tar from the release then run:

`./bin/fauceth`

you can also build the tar via:

`./gradlew distTar`

or directly run tha app from within the repo via:

`./gradlew run`

your working dir needs a file called `fauceth.properties` that looks like this:

```properties
hcaptcha.secret=your_hcaptcha_secret
hcaptcha.sitekey=your_hcaptcha_site_key
```

Make sure to replace the values with yours. You can also inject these properties via environment variables or systen properties.

# Dream

What would be reall nice would be a web3 faucet - if anyone has ideas how to realize it please contact me. The problem I cannot overcome is how to validate a captcha without a backend.

Yea you could use a PoH blockchain like idena or a dapp like Proof of Humanity or brightId - but often you need the faucet to get started with crypto - so there is a hen and a egg problem here.

# License

AGPLv3
