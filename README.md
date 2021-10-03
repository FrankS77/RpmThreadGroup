# RpmThreadGroup
RpmThreadGroup is a JMeter plugin with which it is possible to fire requests at very specific times.

Background: JMeter is a tool to send requests to a server. What is
missing is the possibility to exactly define how much requests should be
send to the server in a given time and the possibility to define this
with a ramp-up. Because in JMeter it can only be defined how much
responses (throughput) should be get from a server (responses to our
requests) in a given time frame. This is a big difference! What we need
in JMeter is the possibility to define 3 values:

1\. The Requests per minute at the beginning

2\. The Requests per minute at the end

3\. The duration of the time interval.

The big difference to all other possibilities is that the number of
threads (users) should be variable. If the number of threads are not
enough because all current threads are waiting for responses but another
request should be send than another thread (user) should be created.

![](.//media/image1.png){width="6.3in" height="2.2719116360454943in"}

Task: Given:

1\. The Requests per minute at the beginning (startRPM)

2\. The Requests per minute at the end (endRPM)

3\. The duration of the time interval. (n)

Find out the two formulas (ascending and descending) to calculate how
much requests should be send to a server at **any** point of time (t).

1.  The amount of requests (R) is defined by the areas R1 and R2.

> ![](.//media/image2.png){width="4.472222222222222in"
> height="0.7222222222222222in"}

We need a formula that doesn't depend on endRPM because we make endRPM
variable over the time.

![Ein Bild, das Tisch enthält. Automatisch generierte
Beschreibung](.//media/image3.png){width="3.3194444444444446in"
height="1.2916666666666667in"}

Replace (endRPM --startRPM) by ( n \* tan α) in formula above.

![](.//media/image4.png){width="3.625in" height="0.6944444444444444in"}

Solve an equation for "n" because we want "n" variable over t.
Intermediate calculation steps are not shown.

Solution formula for ascending requests per minute:

![](.//media/image5.png){width="3.3194444444444446in"
height="1.0833333333333333in"}

!!! We always want the time "n" for the next (1) request. So R = 1 !!!!!

![Ein Bild, das Text enthält. Automatisch generierte
Beschreibung](.//media/image6.png){width="3.3333333333333335in"
height="0.8888888888888888in"}

Now we have "n". startRPM is variable. The endRPM is always the new
startRPM.

startRPMnew = n \* tan$\text{\ α}$ + startRPM

Solution formula for descending requests per minute (no calculation
steps are shown)

![](.//media/image7.png){width="3.6666666666666665in" height="0.875in"}
