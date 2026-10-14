# Reference Study: Ticketing Backend

This is a starting checklist for roadmap slice 0.1, not a substitute for
tracing the reference yourself. The existing implementation lives in the
separate `../ticketing-system/` repository and belongs to its original author.
Do not copy its code into this project.

## Starting observations to verify

- The reference README describes a feature-oriented modular monolith and
  pessimistic locking for inventory.
- `BookingService.createBooking` is transactional. It validates the user/event,
  calls the inventory service, then saves the booking. Verify the transaction
  boundary and rollback behavior yourself.
- `InventoryService.reserveTickets` is also transactional and calls
  `InventoryLockService.lockEvent` before decrementing available tickets.
- `EventRepository.findById` carries `@Lock(PESSIMISTIC_WRITE)`. Confirm that
  the called repository method is invoked inside the active transaction and
  inspect the generated SQL if you want to establish the actual lock behavior.
- The `Event` entity also has `@Version`; compare what optimistic versioning
  adds when the pessimistic lock is already used.
- The visible reference tests are context-load smoke tests. They do not prove
  that concurrent requests cannot oversell. This project will add real
  PostgreSQL integration tests for that claim.
- Booking confirmation invokes notification handling before the transactional
  service method returns. Inspect the notification implementation and consider
  what happens if delivery fails or the database transaction later rolls back.

## Trace these files

- [Reference README](../../../ticketing-system/ticketing-system/README.md)
- [BookingService](../../../ticketing-system/ticketing-system/src/main/java/com/ashish/ticketing/modules/booking/service/BookingService.java)
- [InventoryService](../../../ticketing-system/ticketing-system/src/main/java/com/ashish/ticketing/modules/inventory/service/InventoryService.java)
- [InventoryLockService](../../../ticketing-system/ticketing-system/src/main/java/com/ashish/ticketing/modules/inventory/service/InventoryLockService.java)
- [EventRepository](../../../ticketing-system/ticketing-system/src/main/java/com/ashish/ticketing/modules/event/repository/EventRepository.java)
- [Event](../../../ticketing-system/ticketing-system/src/main/java/com/ashish/ticketing/modules/event/entity/Event.java)
- [First smoke test](../../../ticketing-system/ticketing-system/src/test/java/com/ashish/ticketing/TicketingSystemApplicationTests.java)
- [Second smoke test](../../../ticketing-system/ticketing-system/src/test/java/com/ashish/ticketing/ticketingsystem/TicketingSystemApplicationTests.java)

## Your notes

After doing the trace, write the call sequence, transaction owner, lock query,
and one unresolved question in your own words. Then answer Phase 0's explain-back
questions without looking at this file.