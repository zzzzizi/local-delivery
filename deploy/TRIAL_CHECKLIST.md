# Small-user trial checklist

Start with 3–5 volunteers on a dedicated test URL using fictional names, pickup locations, and delivery details. Do not arrange real purchases or collect payment during the first trial. Hosting and invitations are separate decisions; this document does not send invitations.

## Account journey

- Register a fresh email, open its verification message, confirm, and log in.
- Verify that an unverified account cannot log in, and that an incorrect password gives useful feedback.
- Request another verification link; confirm the old link stops working.
- Open a private page while signed out and confirm the login redirect.
- Request a password reset, set a new password, and check both browser sessions are signed out. Old passwords and used reset links must fail.
- Check that unknown email addresses receive the same recovery response.

## Customer/helper journey

- Customer creates a BUY, PICKUP, or PACKAGE request, sees it in My Requests, and cannot accept it themselves.
- A different helper sees and accepts the OPEN job. It leaves Available Jobs and appears in that helper's My Deliveries.
- A third user cannot view its private details or either person's tracking list.
- Helper progresses through Picked up, Delivering, and Delivered. Customer sees each update after refreshing.
- Customer can cancel a separate OPEN/ACCEPTED request. Helper cannot cancel it, and customer cannot advance delivery status.
- Refresh or double-click an action; confirm it never creates duplicate requests or skips a status.

## Usability and recovery

- Repeat on a phone and a desktop; use keyboard navigation for account forms.
- Check labels, readable errors, expired links, and confirmation feedback.
- Check the loading/retry screen during a temporary service interruption.
- Confirm the deployed health check and actual email delivery work.
- Confirm a backup can be restored into a separate database before adding real operational data.

## Record feedback

For each issue, record:

- Task attempted and device/browser.
- Steps to reproduce.
- Expected result and actual result.
- Impact: blocks use / confusing / cosmetic.
- Screenshot with personal details removed.

Never include passwords, session cookies, email verification/reset links, or real addresses in an issue. Fix blocking issues before expanding the trial. Choose later features from observed needs; payments, GPS, chat, maps, and ratings remain outside Stage 21.
