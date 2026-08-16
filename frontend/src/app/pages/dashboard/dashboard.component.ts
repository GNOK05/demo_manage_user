import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { Project, Task } from '../../core/models';
@Component({standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./dashboard.component.html',styleUrl:'./dashboard.component.scss'})
export class DashboardComponent implements OnInit {projects=signal<Project[]>([]);tasks=signal<Task[]>([]);notice=signal('');constructor(public auth:AuthService,private api:CompanyApiService){}ngOnInit(){this.api.projects().subscribe({next:x=>this.projects.set(x),error:()=>{}});this.api.tasks().subscribe(x=>this.tasks.set(x));}done(){return this.tasks().filter(x=>x.status==='DONE').length;}checkIn(){this.api.checkIn().subscribe({next:()=>this.notice.set('Check-in thành công.'),error:e=>this.notice.set(e.error?.message)});}checkOut(){this.api.checkOut().subscribe({next:()=>this.notice.set('Check-out thành công.'),error:e=>this.notice.set(e.error?.message)});}}
