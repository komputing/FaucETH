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

chain.id=1337702
chain.rpc=https://rpc.kintsugi.themerge.dev
```

Make sure to replace the values with yours. You can also inject these properties via environment variables or system properties.

Optional properties you can set are `app.title`, `app.imageURL`, `app.amount` or `chain.explorer`

You can change the port by setting the environment variable `PORT` or passing it as an command line argument like this:
`./bin/fauceth -port=420`
the default port is `8080`

after you started the app you can see the address by accessing `locahost:8080/admin` - this way you can initially fund it.

# Dream

What would be reall nice would be a web3 faucet - if anyone has ideas how to realize it please contact me. The problem I cannot overcome is how to validate a captcha without a backend.

Yea you could use a PoH blockchain like idena or a dapp like Proof of Humanity or brightId - but often you need the faucet to get started with crypto - so there is a hen and a egg problem here.

# License

AGPLv3
