from enum import Enum


class RequestCategory(str, Enum):
    PACKAGE = "PACKAGE"
    BUY = "BUY"
    PICKUP = "PICKUP"


class RequestStatus(str, Enum):
    OPEN = "OPEN"
    ACCEPTED = "ACCEPTED"
    PICKED_UP = "PICKED_UP"
    DELIVERING = "DELIVERING"
    DELIVERED = "DELIVERED"
    CANCELLED = "CANCELLED"
