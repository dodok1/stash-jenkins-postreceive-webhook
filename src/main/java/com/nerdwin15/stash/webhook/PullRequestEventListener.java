package com.nerdwin15.stash.webhook;

import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.event.api.EventListener;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestReopenedEvent;
import com.nerdwin15.stash.webhook.service.SettingsService;
import com.nerdwin15.stash.webhook.service.eligibility.EligibilityFilterChain;
import com.nerdwin15.stash.webhook.service.eligibility.EventContext;

/**
 * Event listener that listens to PullRequestRescopedEvent events.
 *
 * @author Michael Irwin (mikesir87)
 * @author Melvyn de Kort (lordmatanza)
 */
public class PullRequestEventListener {

  private final EligibilityFilterChain filterChain;
  private final Notifier notifier;
  private final SettingsService settingsService;
  private final PullRequestService pullRequestService;

  /**
   * Construct a new instance.
   * @param filterChain The filter chain to test for eligibility
   * @param notifier The notifier service
   * @param settingsService Service to be used to get the Settings
   * @param pullRequestService
   */
  public PullRequestEventListener(EligibilityFilterChain filterChain,
                                  Notifier notifier, SettingsService settingsService, PullRequestService pullRequestService) {
    this.filterChain = filterChain;
    this.notifier = notifier;
    this.settingsService = settingsService;
    this.pullRequestService = pullRequestService;
  }

  /**
   * Event listener that is notified of pull request open events
   * @param event The pull request event
   */
  @EventListener
  public void onPullRequestOpened(PullRequestOpenedEvent event) {
    handleEvent(event);
  }

  /**
   * Event listener that is notified of pull request reopen events
   * @param event The pull request event
   */
  @EventListener
  public void onPullRequestReopened(PullRequestReopenedEvent event) {
    handleEvent(event);
  }

  /**
   * Event listener that is notified of pull request rescope events
   * <p>Event should cause merging to master repository as described in
   * <a href="https://answers.atlassian.com/questions/239988/change-pull-request-refs-after-commit-instead-of-after-approval-or-workaround'>Atlassian answers</a>
   * This functionality (update pull request refs and sync commits from forked repo) is side-effect of com.atlassian.bitbucket.pull.PullRequestService#canMerge(int, long).
   * </p>
   * @param event The pull request event
   */
  @EventListener
  public void onPullRequestRescope(PullRequestRescopedEvent event) {
    final PullRequest pullRequest = event.getPullRequest();
    if (!event.getPreviousFromHash().equals(pullRequest.getFromRef().getLatestCommit())) {
      // only to-side rescopes - see link above
      // we force update of references in destination repository calling canMerge
      pullRequestService.canMerge(pullRequest.getToRef().getRepository().getId(), pullRequest.getId());
      handleEvent(event);
    }
  }

  /**
   * Actually handles the event that was triggered.
   * (Made protected to make unit testing easier)
   * @param event The event to be handled
   */
  protected void handleEvent(PullRequestEvent event) {
    if (settingsService.getSettings(event.getPullRequest().getToRef()
        .getRepository()) == null) {
      return;
    }

    String strRef = event.getPullRequest().getFromRef().toString()
        .replaceFirst(".*refs/heads/", "");
    String strSha1 = event.getPullRequest().getFromRef().getLatestCommit();

    EventContext context = new EventContext(event,
        event.getPullRequest().getToRef().getRepository(),
        event.getUser().getName());

    if (filterChain.shouldDeliverNotification(context))
      notifier.notifyBackground(context.getRepository(), strRef, strSha1);
  }

}
